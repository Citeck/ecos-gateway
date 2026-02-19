package ru.citeck.ecos.gateway

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.gateway.exception.UserDisabledException
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.webapp.api.apps.EcosRemoteWebAppsApi

class AuthoritiesProviderTest {

    private lateinit var recordsService: RecordsService
    private lateinit var remoteWebAppsApi: EcosRemoteWebAppsApi
    private lateinit var provider: AuthoritiesProvider

    @BeforeEach
    fun setUp() {
        recordsService = mock()
        remoteWebAppsApi = mock()
        provider = AuthoritiesProvider(recordsService, remoteWebAppsApi)
    }

    private fun mockUserAtts(
        authorities: List<String>? = listOf("ROLE_USER"),
        personDisabled: Boolean? = false,
        notExists: Boolean? = false
    ) {
        val atts = AuthoritiesProvider.EmodelUserAuthAtts(authorities, personDisabled, notExists)
        whenever(recordsService.getAtts(any<Any>(), eq(AuthoritiesProvider.EmodelUserAuthAtts::class.java)))
            .thenReturn(atts)
    }

    private fun mockUserAttsSequence(vararg attsList: AuthoritiesProvider.EmodelUserAuthAtts) {
        val stub = whenever(recordsService.getAtts(any<Any>(), eq(AuthoritiesProvider.EmodelUserAuthAtts::class.java)))
        var ongoingStub = stub.thenReturn(attsList[0])
        for (i in 1 until attsList.size) {
            ongoingStub = ongoingStub.thenReturn(attsList[i])
        }
    }

    @Test
    fun `blank username returns empty authorities`() {
        assertThat(provider.getAuthorities("")).isEmpty()
        assertThat(provider.getAuthorities("   ")).isEmpty()
    }

    @Test
    fun `guest user returns ROLE_GUEST`() {
        val authorities = provider.getAuthorities("guest")
        assertThat(authorities).containsExactly(AuthRole.GUEST)
    }

    @Test
    fun `system user throws error`() {
        assertThatThrownBy { provider.getAuthorities("system") }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("System user can't use gateway")
    }

    @Test
    fun `normal user returns authorities from RecordsService`() {
        mockUserAtts(authorities = listOf("ROLE_USER", "GROUP_all"))
        val authorities = provider.getAuthorities("testuser")
        assertThat(authorities).containsExactly("ROLE_USER", "GROUP_all")
    }

    @Test
    fun `disabled user throws UserDisabledException`() {
        mockUserAtts(personDisabled = true)
        assertThatThrownBy { provider.getAuthorities("disableduser") }
            .isInstanceOf(UserDisabledException::class.java)
            .hasMessageContaining("User is disabled")
    }

    @Test
    fun `disabled admin is NOT blocked`() {
        mockUserAtts(authorities = listOf("ROLE_ADMIN"), personDisabled = true)
        val authorities = provider.getAuthorities("admin")
        assertThat(authorities).containsExactly("ROLE_ADMIN")
    }

    @Test
    fun `non-existent admin throws not created yet error`() {
        mockUserAtts(notExists = true)
        assertThatThrownBy { provider.getAuthorities("admin") }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("not created yet")
    }

    @Test
    fun `non-existent user triggers auto-creation`() {
        val notExistsAtts = AuthoritiesProvider.EmodelUserAuthAtts(listOf("ROLE_USER"), false, true)
        val existsAtts = AuthoritiesProvider.EmodelUserAuthAtts(listOf("ROLE_USER"), false, false)
        mockUserAttsSequence(notExistsAtts, notExistsAtts, existsAtts)

        val authorities = provider.getAuthorities("newuser")
        assertThat(authorities).containsExactly("ROLE_USER")
        verify(recordsService).create(eq("emodel/person"), any<Any>())
    }

    @Test
    fun `null authorities throws error`() {
        mockUserAtts(authorities = null)
        assertThatThrownBy { provider.getAuthorities("testuser") }
            .hasRootCauseInstanceOf(IllegalStateException::class.java)
            .rootCause()
            .hasMessageContaining("authorities is null")
    }

    @Test
    fun `cache deduplicates calls`() {
        mockUserAtts(authorities = listOf("ROLE_USER"))
        provider.getAuthorities("cacheduser")
        provider.getAuthorities("cacheduser")
        verify(recordsService, times(1))
            .getAtts(any<Any>(), eq(AuthoritiesProvider.EmodelUserAuthAtts::class.java))
    }
}
