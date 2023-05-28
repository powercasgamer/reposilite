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

package com.reposilite

import com.reposilite.configuration.local.LocalConfigurationFactory
import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import com.reposilite.journalist.Channel
import com.reposilite.journalist.Journalist
import com.reposilite.journalist.backend.PrintStreamLogger
import com.reposilite.plugin.Extensions
import com.reposilite.plugin.PluginLoader
import com.reposilite.shared.extensions.LoomExtensions
import com.reposilite.shared.extensions.newFixedThreadPool
import com.reposilite.shared.extensions.newSingleThreadScheduledExecutor
import com.reposilite.web.HttpServer
import panda.utilities.console.Effect
import kotlin.io.path.absolutePathString

object ReposiliteFactory {

    fun createReposilite(parameters: ReposiliteParameters): Reposilite =
        createReposilite(parameters, PrintStreamLogger(System.out, System.err, Channel.ALL, false))

    fun createReposilite(parameters: ReposiliteParameters, rootJournalist: Journalist): Reposilite {
        val localConfiguration = LocalConfigurationFactory.createLocalConfiguration(null, parameters)
        parameters.applyLoadedConfiguration(localConfiguration)

        val journalist = ReposiliteJournalist(
            visibleJournalist = rootJournalist,
            cachedLogSize = localConfiguration.cachedLogSize.get(),
            defaultVisibilityThreshold = Channel.of(parameters.level).orElseGet { Channel.INFO },
            testEnv = parameters.testEnv
        )

        journalist.logger.info("")
        journalist.logger.info("${Effect.GREEN}Reposilite ${Effect.RESET}$VERSION")
        journalist.logger.info("")
        journalist.logger.info("--- Environment")
        journalist.logger.info(
            "Platform: ${System.getProperty("java.version")} (${System.getProperty("os.name")} :: ${System.getProperty(
                "os.arch"
            )})"
        )
        journalist.logger.info("Running as: ${System.getProperty("user.name")}")
        journalist.logger.info("Working directory: ${parameters.workingDirectory.toAbsolutePath()}")
        journalist.logger.info("Plugin directory: ${parameters.pluginDirectory.toAbsolutePath()}")
        journalist.logger.info("Configuration: ${parameters.localConfigurationPath.absolutePathString()}")
        journalist.logger.info(
            "Threads: ${localConfiguration.webThreadPool.get()} WEB / ${localConfiguration.ioThreadPool.get()} IO / ${localConfiguration.databaseThreadPool.get()} DB"
        )
        journalist.logger.info("Loom enabled: ${LoomExtensions.isLoomAvailable()}")
        if (parameters.testEnv) journalist.logger.info("Test environment: Enabled")

        val reposilite = Reposilite(
            journalist = journalist,
            parameters = parameters,
            localConfiguration = localConfiguration,
            databaseConnection = DatabaseConnectionFactory.createConnection(
                workingDirectory = parameters.workingDirectory,
                databaseConfiguration = parameters.database,
                databaseThreadPoolSize = localConfiguration.databaseThreadPool.get()
            ),
            webServer = HttpServer(),
            ioService = newFixedThreadPool(
                min = 0,
                max = localConfiguration.ioThreadPool.get(),
                prefix = "Reposilite | IO"
            ),
            scheduler = newSingleThreadScheduledExecutor("Reposilite | Scheduler"),
            extensions = Extensions(journalist)
        )

        journalist.logger.info("")
        journalist.logger.info("--- Loading plugins:")

        val pluginLoader = PluginLoader(parameters.pluginDirectory, reposilite.extensions)
        pluginLoader.extensions.registerFacade(reposilite)
        pluginLoader.loadPluginsByServiceFiles()
        pluginLoader.initialize()

        return reposilite
    }

}
