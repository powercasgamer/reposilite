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

package com.reposilite.statistics.api

import com.reposilite.maven.api.Identifier

/* Per repository */

data class AllResolvedResponse(
    val statisticsEnabled: Boolean = true,
    val repositories: Collection<RepositoryStatistics> = emptyList()
)

data class RepositoryStatistics(
    val name: String,
    val data: List<IntervalRecord>
)

data class IntervalRecord(
    /** Epoch millis in UTC */
    val date: Long,
    val count: Long
)

/* Per identifier */

data class IncrementResolvedRequest(
    val identifier: Identifier,
    val count: Long = 1
)

data class ResolvedCountResponse(
    val sum: Long,
    val requests: List<ResolvedEntry>
)

data class ResolvedEntry(
    val gav: String,
    val count: Long
)
