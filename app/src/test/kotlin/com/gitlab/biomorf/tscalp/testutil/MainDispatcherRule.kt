package com.gitlab.biomorf.tscalp.testutil

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit4-rule: подменяет `Dispatchers.Main` на `TestDispatcher`.
 *
 * Нужен для тестов ViewModel: `viewModelScope` использует
 * `Dispatchers.Main.immediate`, без подмены `runTest` их не выполнит.
 *
 * Создан в рамках M006 (первый panel-VM с тестами).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
