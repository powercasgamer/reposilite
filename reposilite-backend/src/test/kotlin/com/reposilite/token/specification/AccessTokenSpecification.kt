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

package com.reposilite.token.specification

import com.reposilite.journalist.backend.AggregatedLogger
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.journalist.backend.PrintStreamLogger
import com.reposilite.token.AccessTokenFacade
import com.reposilite.token.AccessTokenType.TEMPORARY
import com.reposilite.token.ExportService
import com.reposilite.token.api.AccessTokenDto
import com.reposilite.token.api.CreateAccessTokenRequest
import com.reposilite.token.api.CreateAccessTokenResponse
import com.reposilite.token.infrastructure.InMemoryAccessTokenRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

internal abstract class AccessTokenSpecification {

    val logger = AggregatedLogger(InMemoryLogger(), PrintStreamLogger(System.out, System.out))

    @TempDir
    lateinit var workingDirectory: File
    lateinit var accessTokenFacade: AccessTokenFacade

    @BeforeEach
    fun createAccessTokenFacade() {
        this.accessTokenFacade = AccessTokenFacade(
            logger,
            InMemoryAccessTokenRepository(),
            InMemoryAccessTokenRepository(),
            ExportService()
        )
    }

    fun createToken(name: String): CreateAccessTokenResponse =
        accessTokenFacade.createAccessToken(CreateAccessTokenRequest(TEMPORARY, name))

    fun createToken(name: String, secret: String): AccessTokenDto =
        accessTokenFacade.createAccessToken(CreateAccessTokenRequest(TEMPORARY, name, secret = secret)).accessToken

    fun deleteAllTokens() {
        accessTokenFacade.getAccessTokens().forEach { accessTokenFacade.deleteToken(it.identifier) }
    }

    fun workingDirectory(): Path =
        workingDirectory.toPath()

}
