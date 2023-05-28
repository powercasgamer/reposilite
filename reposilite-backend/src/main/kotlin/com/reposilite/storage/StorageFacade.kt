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

package com.reposilite.storage

import com.reposilite.plugin.api.Facade
import com.reposilite.status.FailureFacade
import java.nio.file.Path
import java.util.ServiceLoader

class StorageFacade : Facade {

    @Suppress("UNCHECKED_CAST")
    private val storageProviderFactories = ServiceLoader.load(StorageProviderFactory::class.java)
        .associateBy { it.type }
        .mapValues { (_, factory) -> factory as StorageProviderFactory<*, StorageProviderSettings> }

    fun createStorageProvider(failureFacade: FailureFacade, workingDirectory: Path, repository: String, storageSettings: StorageProviderSettings): StorageProvider? =
        storageProviderFactories[storageSettings.type]?.create(
            failureFacade,
            workingDirectory,
            repository,
            storageSettings
        )

}
