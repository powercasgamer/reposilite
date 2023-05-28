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

package com.reposilite.frontend.application

import com.reposilite.Reposilite
import com.reposilite.configuration.local.LocalConfiguration
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.frontend.FrontendFacade
import com.reposilite.frontend.infrastructure.CustomFrontendHandler
import com.reposilite.frontend.infrastructure.NotFoundHandler
import com.reposilite.frontend.infrastructure.ResourcesFrontendHandler
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposiliteInitializeEvent
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.plugin.event
import com.reposilite.plugin.facade
import com.reposilite.web.api.HttpServerInitializationEvent
import com.reposilite.web.api.ReposiliteRoutes
import com.reposilite.web.api.RoutingSetupEvent
import io.javalin.http.NotFoundResponse
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

@Plugin(
    name = "frontend",
    dependencies = ["local-configuration", "shared-configuration"],
    settings = FrontendSettings::class
)
internal class FrontendPlugin : ReposilitePlugin() {

    internal companion object {
        private const val STATIC_DIRECTORY = "static"
        private const val FRONTEND_DIRECTORY = "reposilite-frontend"
        private const val INDEX = "index.html"
        private const val FAVICON = "favicon.png"
    }

    override fun initialize(): FrontendFacade {
        val localConfiguration = facade<LocalConfiguration>()
        val sharedConfigurationFacade = facade<SharedConfigurationFacade>()
        val frontendSettings = sharedConfigurationFacade.getDomainSettings<FrontendSettings>()

        val frontendFacade = FrontendComponents(
            basePath = localConfiguration.basePath,
            frontendSettings = frontendSettings
        ).frontendFacade()

        event { event: ReposiliteInitializeEvent ->
            val staticDirectory = staticDirectory(event.reposilite)

            staticDirectory
                .takeUnless { it.exists() }
                ?.also { Files.createDirectory(it) }
                ?.also { copyResourceTo(INDEX, it.resolve(INDEX)) }

            staticDirectory.resolve(FAVICON)
                .takeUnless { it.exists() }
                ?.also { copyResourceTo(FAVICON, it) }
        }

        event { event: RoutingSetupEvent ->
            val routes = mutableSetOf<ReposiliteRoutes>()
            if (localConfiguration.defaultFrontend.get()) {
                routes.add(ResourcesFrontendHandler(frontendFacade, FRONTEND_DIRECTORY))
            }
            routes.add(CustomFrontendHandler(frontendFacade, staticDirectory(event.reposilite)))
            event.registerRoutes(routes)
        }

        event { event: HttpServerInitializationEvent ->
            event.javalin.exception(NotFoundResponse::class.java, NotFoundHandler(frontendFacade))
            event.javalin.error(404, NotFoundHandler(frontendFacade))
        }

        return frontendFacade
    }

    private fun copyResourceTo(file: String, to: Path) {
        Reposilite::class.java.getResourceAsStream("/$STATIC_DIRECTORY/$file")?.use {
            Files.copy(it, to)
        }
    }

    private fun staticDirectory(reposilite: Reposilite): Path =
        reposilite.parameters.workingDirectory.resolve(STATIC_DIRECTORY)

}
