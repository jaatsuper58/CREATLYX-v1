package com.chattlyx.data.backup

import com.chattlyx.domain.backup.BackupRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** BKP-01/02 bindings. */
@Module
@InstallIn(SingletonComponent::class)
interface BackupDataModule {

    @Binds
    fun backupRepository(impl: BackupRepositoryImpl): BackupRepository
}
