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
package com.reposilite.configuration.local

import com.reposilite.plugin.api.Facade
import com.reposilite.shared.extensions.Validator
import net.dzikoysk.cdn.entity.Description
import panda.std.reactive.mutableReference
import panda.std.reactive.reference
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

class LocalConfiguration : Facade {

    /* General */

    @Description("# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ #")
    @Description("#       Reposilite :: Local       #")
    @Description("# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ #")
    @Description("")
    @Description("# Local configuration contains init params for current Reposilite instance.")
    @Description(
        "# For more options, shared between instances, login to the dashboard with management token and visit 'Configuration' tab."
    )
    @Description("")
    @Description("# Hostname")
    @Description("# The hostname can be used to limit which connections are accepted.")
    @Description("# Use 0.0.0.0 to accept connections from anywhere." )
    @Description("# 127.0.0.1 will only allow connections from localhost.")
    val hostname = reference("0.0.0.0")

    @Description("# Port to bind")
    val port = reference(8080)

    /* Database */

    @Description("# Database configuration. Supported storage providers:")
    @Description("# - mysql localhost:3306 database user password")
    @Description("# - sqlite reposilite.db")
    @Description("# - sqlite --temporary")
    @Description("# Experimental providers (not covered with tests):")
    @Description("# - postgresql localhost:5432 database user password")
    @Description("# - h2 reposilite")
    val database = reference("sqlite reposilite.db")

    @Command(name = "embedded")
    internal class EmbeddedSQLDatabaseSettings : Validator() {
        @Parameters(index = "0", paramLabel = "<file-name>", defaultValue = "")
        var fileName: String = ""

        @Option(names = ["--temporary", "--temp", "-t"])
        var temporary = false
    }

    @Command(name = "standard")
    internal class StandardSQLDatabaseSettings : Validator() {
        @Parameters(index = "0", paramLabel = "<host>")
        lateinit var host: String

        @Parameters(index = "1", paramLabel = "<database>")
        lateinit var database: String

        @Parameters(index = "2", paramLabel = "<user>")
        lateinit var user: String

        @Parameters(index = "3", paramLabel = "<password>")
        lateinit var password: String
    }

    /* SSL */

    @Description("")
    @Description("# Support encrypted connections")
    val sslEnabled = reference(false)

    @Description("# SSL port to bind")
    val sslPort = reference(443)

    @Description("# Key file to use.")
    @Description("# You can specify absolute path to the given file or use \${WORKING_DIRECTORY} variable.")
    @Description("# If you want to use .pem certificate you need to specify its path next to the key path.")
    @Description("# Example .pem paths setup:")
    @Description("# keyPath: \${WORKING_DIRECTORY}/cert.pem \${WORKING_DIRECTORY}/key.pem")
    @Description("# Example .jks path setup:")
    @Description("# keyPath: \${WORKING_DIRECTORY}/keystore.jks")
    val keyPath = reference("\${WORKING_DIRECTORY}/cert.pem \${WORKING_DIRECTORY}/key.pem")

    @Description("# Key password to use")
    val keyPassword = reference("")

    @Description("# Redirect http traffic to https")
    val enforceSsl = reference(false)

    /* Performance */

    @Description("")
    @Description("# Max amount of threads used by core thread pool (min: 5)")
    @Description(
        "# The web thread pool handles first few steps of incoming http connections, as soon as possible all tasks are redirected to IO thread pool."
    )
    val webThreadPool = reference(16)

    @Description("# IO thread pool handles all tasks that may benefit from non-blocking IO (min: 2)")
    @Description(
        "# Because most of tasks are redirected to IO thread pool, it might be a good idea to keep it at least equal to web thread pool."
    )
    val ioThreadPool = reference(8)

    @Description("# Database thread pool manages open connections to database (min: 1)")
    @Description(
        "# Embedded databases such as SQLite or H2 don't support truly concurrent connections, so the value will be always 1 for them if selected."
    )
    val databaseThreadPool = reference(1)

    @Description("# Select compression strategy used by this instance.")
    @Description("# Using 'none' reduces usage of CPU & memory, but ends up with higher transfer usage.")
    @Description(
        "# GZIP is better option if you're not limiting resources that much to increase overall request times."
    )
    @Description("# Available strategies: none, gzip")
    val compressionStrategy = reference("none")

    @Description("# Default idle timeout used by Jetty")
    val idleTimeout = reference(30_000L)

    /* Cache */

    @Description("", "# Adds cache bypass headers to each request from /api/* scope served by this instance.")
    @Description("# Helps to avoid various random issues caused by proxy provides (e.g. Cloudflare) and browsers.")
    val bypassExternalCache = reference(true)

    @Description("# Amount of messages stored in cached logger.")
    val cachedLogSize = reference(32)

    /* Frontend */

    @Description("# Enable default frontend with dashboard")
    val defaultFrontend = mutableReference(true)

    @Description("# Set custom base path for Reposilite instance.")
    @Description("# It's not recommended to mount Reposilite under custom base path")
    @Description("# and you should always prioritize subdomain over this option.")
    val basePath = mutableReference("/")

    /* Others */

    @Description("# Debug mode")
    val debugEnabled = reference(false)

}
