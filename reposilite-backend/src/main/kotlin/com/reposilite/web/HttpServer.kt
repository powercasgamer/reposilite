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

package com.reposilite.web

import com.reposilite.Reposilite
import com.reposilite.configuration.local.LocalConfiguration
import com.reposilite.web.api.HttpServerInitializationEvent
import com.reposilite.web.api.HttpServerStartedEvent
import com.reposilite.web.api.HttpServerStoppedEvent
import com.reposilite.web.application.JavalinConfiguration
import io.javalin.Javalin
import io.javalin.util.ConcurrencyUtil
import org.eclipse.jetty.io.EofException
import org.eclipse.jetty.util.thread.ThreadPool

class HttpServer {

    private val servlet = false
    private var javalin: Javalin? = null

    fun start(reposilite: Reposilite) {
        val extensionsManagement = reposilite.extensions
        val localConfiguration = extensionsManagement.facade<LocalConfiguration>()

        val webThreadPool = ConcurrencyUtil.jettyThreadPool(
            name = "Reposilite | Web (${localConfiguration.webThreadPool.get()}) -",
            minThreads = localConfiguration.webThreadPool.get(),
            maxThreads = localConfiguration.webThreadPool.get()
        )

        this.javalin = createJavalin(reposilite, webThreadPool)
            .exception(EofException::class.java) { _, _ -> reposilite.logger.warn("Client closed connection") }
            .events { listener ->
                listener.serverStopping { reposilite.logger.info("Server stopping...") }
                listener.serverStopped { extensionsManagement.emitEvent(HttpServerStoppedEvent) }
            }
            .also {
                reposilite.extensions.emitEvent(HttpServerInitializationEvent(reposilite, it))
            }

        if (!servlet) {
            javalin!!.start(reposilite.parameters.hostname, reposilite.parameters.port)
            extensionsManagement.emitEvent(HttpServerStartedEvent)
        }
    }

    private fun createJavalin(reposilite: Reposilite, webThreadPool: ThreadPool): Javalin =
        if (servlet) {
            Javalin.createStandalone { JavalinConfiguration.configure(reposilite, webThreadPool, it) }
        } else {
            Javalin.create { JavalinConfiguration.configure(reposilite, webThreadPool, it) }
        }

    fun stop() {
        javalin?.stop()
    }

    fun isAlive(): Boolean =
        javalin?.jettyServer()?.server()?.isStarted ?: false

}
