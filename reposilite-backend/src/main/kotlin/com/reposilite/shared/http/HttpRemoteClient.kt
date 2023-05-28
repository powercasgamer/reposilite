/*
 * Copyright (c) 2023 dzikoysk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.reposilite.shared.http

import com.google.api.client.http.GenericUrl
import com.google.api.client.http.HttpMethods
import com.google.api.client.http.HttpRequest
import com.google.api.client.http.HttpResponse
import com.google.api.client.http.javanet.NetHttpTransport
import com.reposilite.journalist.Channel
import com.reposilite.journalist.Journalist
import com.reposilite.journalist.Logger
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.badRequestError
import com.reposilite.shared.toErrorResult
import com.reposilite.storage.api.DocumentInfo
import com.reposilite.storage.api.FileDetails
import com.reposilite.storage.api.UNKNOWN_LENGTH
import com.reposilite.storage.api.toLocation
import com.reposilite.storage.getExtension
import io.javalin.http.ContentType
import io.javalin.http.HttpStatus.NOT_ACCEPTABLE
import panda.std.Result
import panda.std.asSuccess
import java.io.InputStream
import java.net.Proxy

interface RemoteClientProvider {

    fun createClient(journalist: Journalist, proxy: Proxy?): RemoteClient

}

object HttpRemoteClientProvider : RemoteClientProvider {

    override fun createClient(journalist: Journalist, proxy: Proxy?): RemoteClient =
        HttpRemoteClient(journalist, proxy)

}

class HttpRemoteClient(private val journalist: Journalist, proxy: Proxy?) : RemoteClient, Journalist {

    private val requestFactory = NetHttpTransport.Builder()
        .setProxy(proxy)
        .build()
        .createRequestFactory()

    override fun head(uri: String, credentials: RemoteCredentials?, connectTimeoutInSeconds: Int, readTimeoutInSeconds: Int): Result<FileDetails, ErrorResponse> =
        createRequest(HttpMethods.HEAD, uri, credentials, connectTimeoutInSeconds, readTimeoutInSeconds)
            .execute { response ->
                response.disconnect()
                val headers = response.headers

                // Nexus can send misleading for client content-length of chunked responses
                // ~ https://github.com/dzikoysk/reposilite/issues/549
                val contentLength = headers.contentLength
                    ?.takeUnless { "gzip" == headers.contentEncoding } // remove content-length header
                    ?: UNKNOWN_LENGTH

                val contentType = headers.contentType
                    ?.let { ContentType.getContentType(it) }
                    ?: ContentType.getContentTypeByExtension(uri.getExtension())
                    ?: ContentType.APPLICATION_OCTET_STREAM

                DocumentInfo(
                    uri.toLocation().getSimpleName(),
                    contentType,
                    contentLength
                ).asSuccess()
            }

    override fun get(uri: String, credentials: RemoteCredentials?, connectTimeoutInSeconds: Int, readTimeoutInSeconds: Int): Result<InputStream, ErrorResponse> =
        createRequest(HttpMethods.GET, uri, credentials, connectTimeoutInSeconds, readTimeoutInSeconds)
            .execute { it.content.asSuccess() }

    private fun createRequest(method: String, uri: String, credentials: RemoteCredentials?, connectTimeout: Int, readTimeout: Int): HttpRequest {
        val request = requestFactory.buildRequest(method, GenericUrl(uri), null)
        request.throwExceptionOnExecuteError = false
        request.connectTimeout = connectTimeout * 1000
        request.readTimeout = readTimeout * 1000
        request.authenticateWith(credentials)
        return request
    }

    private fun <R> HttpRequest.execute(consumer: (HttpResponse) -> Result<R, ErrorResponse>): Result<R, ErrorResponse> =
        try {
            val response = this.execute()
            logger.debug(
                "HttpRemoteClient | $url responded with ${response.statusCode} (Content-Type: ${response.contentType})"
            )

            when {
                response.contentType == ContentType.HTML -> NOT_ACCEPTABLE.toErrorResult(
                    "Illegal file type (${response.contentType})"
                )
                response.isSuccessStatusCode.not() -> NOT_ACCEPTABLE.toErrorResult(
                    "Unsuccessful request (${response.statusCode})"
                )
                else -> consumer(response)
            }.onError {
                response.disconnect()
            }
        } catch (exception: Exception) {
            createExceptionResponse(this.url.toString(), exception)
        }

    private fun HttpRequest.authenticateWith(credentials: RemoteCredentials?): HttpRequest = also {
        if (credentials != null) {
            when (credentials.method) {
                AuthenticationMethod.BASIC -> it.headers.setBasicAuthentication(credentials.login, credentials.password)
                AuthenticationMethod.CUSTOM_HEADER -> it.headers[credentials.login] = credentials.password
            }
        }
    }

    private fun <V> createExceptionResponse(uri: String, exception: Exception): Result<V, ErrorResponse> {
        logger.debug("HttpRemoteClient | Cannot get $uri")
        logger.exception(Channel.DEBUG, exception)
        return badRequestError("An error of type ${exception.javaClass} happened: ${exception.message}")
    }

    override fun getLogger(): Logger =
        journalist.logger

}
