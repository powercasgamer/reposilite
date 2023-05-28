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

package com.reposilite.maven.api

import com.reposilite.maven.Repository
import com.reposilite.plugin.api.Event
import com.reposilite.shared.ErrorResponse
import com.reposilite.storage.api.DocumentInfo
import com.reposilite.storage.api.Location
import com.reposilite.token.AccessTokenIdentifier
import panda.std.Result
import java.io.InputStream

data class LookupRequest(
    val accessToken: AccessTokenIdentifier?,
    val repository: String,
    val gav: Location,
)

data class VersionLookupRequest(
    val accessToken: AccessTokenIdentifier?,
    val repository: Repository,
    val gav: Location,
    val filter: String? = null
)

data class VersionsResponse(
    val isSnapshot: Boolean,
    val versions: List<String>
)

data class PreResolveEvent(
    val accessToken: AccessTokenIdentifier?,
    val repository: Repository,
    val gav: Location
) : Event

data class ResolvedFileEvent(
    val accessToken: AccessTokenIdentifier?,
    val repository: Repository,
    val gav: Location,
    var result: Result<Pair<DocumentInfo, InputStream>, ErrorResponse>
) : Event
