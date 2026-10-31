package com.example.data

import android.net.Network
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.net.URLEncoder

data class PortalResponse(
    val isSuccess: Boolean,
    val status: String,
    val message: String,
    val rawXml: String = ""
)

object PortalRepository {
    const val PORTAL_HOST = "172.16.16.16"
    const val PORTAL_PORT = 8090
    private const val LOGIN_ENDPOINT = "http://172.16.16.16:8090/login.xml"
    private const val REFERER_URL = "http://172.16.16.16:8090/httpclient.html"
    private const val ORIGIN_URL = "http://172.16.16.16:8090"

    private const val TIMEOUT_CONNECT_MS = 5000
    private const val TIMEOUT_READ_MS = 5000
    private const val REACHABILITY_TIMEOUT_MS = 3000

    /**
     * Checks if 172.16.16.16:8090 is reachable within 3 seconds,
     * routing specifically through the given network if provided.
     */
    fun isPortalReachable(network: Network? = null): Boolean {
        var socket: Socket? = null
        return try {
            socket = if (network != null) {
                network.socketFactory.createSocket()
            } else {
                Socket()
            }
            socket.connect(InetSocketAddress(PORTAL_HOST, PORTAL_PORT), REACHABILITY_TIMEOUT_MS)
            true
        } catch (_: Exception) {
            false
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Executes login to Sophos Captive Portal with up to 2 retries on transient network failures.
     */
    fun performLogin(
        username: String,
        password: String,
        network: Network? = null,
        maxRetries: Int = 2
    ): PortalResponse {
        var attempt = 0
        var lastResponse = PortalResponse(
            isSuccess = false,
            status = "FAILED",
            message = "Unable to connect to campus portal"
        )

        while (attempt <= maxRetries) {
            val response = executeLoginRequest(username, password, network)
            if (response.isSuccess) {
                return response
            }
            lastResponse = response
            attempt++
            if (attempt <= maxRetries) {
                try {
                    Thread.sleep(1500)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
        return lastResponse
    }

    private fun executeLoginRequest(
        username: String,
        password: String,
        network: Network?
    ): PortalResponse {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(LOGIN_ENDPOINT)
            conn = if (network != null) {
                network.openConnection(url) as HttpURLConnection
            } else {
                url.openConnection() as HttpURLConnection
            }

            conn.requestMethod = "POST"
            conn.instanceFollowRedirects = false
            conn.connectTimeout = TIMEOUT_CONNECT_MS
            conn.readTimeout = TIMEOUT_READ_MS
            conn.doOutput = true
            conn.doInput = true

            // Set required headers for Sophos Captive Portal
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.setRequestProperty("Origin", ORIGIN_URL)
            conn.setRequestProperty("Referer", REFERER_URL)
            conn.setRequestProperty("Connection", "close")

            val epochMillis = System.currentTimeMillis().toString()
            val postData = buildString {
                append("mode=191")
                append("&username=").append(URLEncoder.encode(username, "UTF-8"))
                append("&password=").append(URLEncoder.encode(password, "UTF-8"))
                append("&a=").append(epochMillis)
                append("&producttype=0")
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(postData)
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return PortalResponse(
                    isSuccess = false,
                    status = "HTTP_$responseCode",
                    message = "Server returned HTTP status $responseCode"
                )
            }

            val responseBody = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                reader.readText()
            }

            parsePortalXmlResponse(responseBody)
        } catch (e: Exception) {
            PortalResponse(
                isSuccess = false,
                status = "ERROR",
                message = e.localizedMessage ?: "Connection error"
            )
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Executes logout from the Sophos Captive Portal (mode=193).
     */
    fun performLogout(username: String, network: Network? = null): PortalResponse {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(LOGIN_ENDPOINT)
            conn = if (network != null) {
                network.openConnection(url) as HttpURLConnection
            } else {
                url.openConnection() as HttpURLConnection
            }

            conn.requestMethod = "POST"
            conn.instanceFollowRedirects = false
            conn.connectTimeout = TIMEOUT_CONNECT_MS
            conn.readTimeout = TIMEOUT_READ_MS
            conn.doOutput = true
            conn.doInput = true

            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.setRequestProperty("Origin", ORIGIN_URL)
            conn.setRequestProperty("Referer", REFERER_URL)
            conn.setRequestProperty("Connection", "close")

            val epochMillis = System.currentTimeMillis().toString()
            val postData = buildString {
                append("mode=193")
                append("&username=").append(URLEncoder.encode(username, "UTF-8"))
                append("&a=").append(epochMillis)
                append("&producttype=0")
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(postData)
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return PortalResponse(
                    isSuccess = false,
                    status = "HTTP_$responseCode",
                    message = "Logout HTTP error: $responseCode"
                )
            }

            val responseBody = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                reader.readText()
            }

            parsePortalXmlResponse(responseBody)
        } catch (e: Exception) {
            PortalResponse(
                isSuccess = false,
                status = "ERROR",
                message = e.localizedMessage ?: "Logout failed"
            )
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Parses the ~280-byte XML response from the portal.
     * Extracts <status> and <message>.
     * Sophos responses typically:
     * <response transactionid="">
     *   <status>LIVE</status> or <status>LOGIN</status> or <status>FAILED</status>
     *   <message><![CDATA[You have successfully logged in]]></message>
     * </response>
     */
    fun parsePortalXmlResponse(xmlContent: String): PortalResponse {
        if (xmlContent.isBlank()) {
            return PortalResponse(isSuccess = false, status = "EMPTY", message = "Empty response from portal")
        }

        return try {
            var status = ""
            var message = ""

            // Fast, ultra-reliable extraction for Sophos ~280-byte XML response
            val statusMatch = Regex("<status[^>]*>(.*?)</status>", RegexOption.DOT_MATCHES_ALL).find(xmlContent)
            if (statusMatch != null) {
                status = statusMatch.groupValues[1].trim()
            }

            val msgMatch = Regex("<message[^>]*>(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?</message>", RegexOption.DOT_MATCHES_ALL).find(xmlContent)
            if (msgMatch != null) {
                message = msgMatch.groupValues[1].trim()
            }

            // Fallback to XmlPullParser
            if (status.isEmpty() && message.isEmpty()) {
                val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance()
                val parser = factory.newPullParser()
                parser.setInput(StringReader(xmlContent))
                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG) {
                        when (parser.name.lowercase()) {
                            "status" -> status = parser.nextText().trim()
                            "message" -> message = parser.nextText().trim()
                        }
                    }
                    eventType = parser.next()
                }
            }

            // Treat non-portal/unparseable XML as failed login
            if (status.isEmpty() && message.isEmpty()) {
                return PortalResponse(
                    isSuccess = false,
                    status = "PARSE_ERROR",
                    message = "Unrecognized response from server",
                    rawXml = xmlContent
                )
            }

            val statusUpper = status.uppercase()
            val msgLower = message.lowercase()

            val isSuccess = (statusUpper == "LIVE" || statusUpper == "SUCCESS" || msgLower.contains("successfully logged in")) &&
                !msgLower.contains("wrong") &&
                !msgLower.contains("failed") &&
                !msgLower.contains("limit") &&
                !msgLower.contains("exceeded") &&
                !msgLower.contains("blocked") &&
                !msgLower.contains("invalid")

            val displayMessage = when {
                message.isNotBlank() -> message
                isSuccess -> "Logged in successfully"
                status.isNotBlank() -> "Portal status: $status"
                else -> "Failed to log in"
            }

            PortalResponse(
                isSuccess = isSuccess,
                status = if (status.isNotBlank()) status else if (isSuccess) "SUCCESS" else "FAILED",
                message = displayMessage,
                rawXml = xmlContent
            )
        } catch (e: Exception) {
            // Treat unparseable XML as a failed login per prompt instructions
            PortalResponse(
                isSuccess = false,
                status = "PARSE_ERROR",
                message = "Invalid response format: ${e.message}",
                rawXml = xmlContent
            )
        }
    }
}
