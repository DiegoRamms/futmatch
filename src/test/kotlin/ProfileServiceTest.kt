import com.devapplab.data.database.executor.DbExecutor
import com.devapplab.data.repository.RefreshTokenRepository
import com.devapplab.data.repository.match.MatchRepository
import com.devapplab.data.repository.user.UserRepository
import com.devapplab.model.auth.RefreshTokenRecord
import com.devapplab.model.auth.RefreshTokenStatus
import com.devapplab.model.auth.RefreshTokenStatusReason
import com.devapplab.model.auth.RefreshTokenValidationRecord
import com.devapplab.model.AppResult
import com.devapplab.model.auth.UserSignInInfo
import com.devapplab.model.match.*
import com.devapplab.model.payment.PaymentProvider
import com.devapplab.model.user.*
import com.devapplab.model.user.response.OrganizerListItem
import com.devapplab.observability.AppRequestContext
import com.devapplab.model.user.request.UpdateManagedUserAccessRequest
import com.devapplab.service.AdminUserService
import com.devapplab.service.ProfileService
import com.devapplab.service.image.ImageService
import io.ktor.http.content.*
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProfileServiceTest {

    private val testContext = AppRequestContext(
        requestId = "test-request",
        method = "GET",
        path = "/profile"
    )

    @Test
    fun `getMyProfile returns success without lastMatch`() = kotlinx.coroutines.runBlocking {
        val userId = UUID.randomUUID()
        val service = ProfileService(
            dbExecutor = FakeDbExecutor(),
            userRepository = FakeUserRepository(user = fakeUser(userId)),
            matchRepository = FakeMatchRepository(
                winStats = HomeWinStats(playedMatches = 10, wonMatches = 8),
                mvpCount = 3,
                totalGoals = 21,
                lastMatch = null
            ),
            imageService = FakeImageService()
        )

        val result = service.getMyProfile(userId, Locale.US, testContext)
        val success = result as AppResult.Success

        assertEquals(userId, success.data.id)
        assertEquals(80, success.data.averageScore)
        assertEquals(10, success.data.stats.matchesPlayed)
        assertEquals(8, success.data.stats.matchesWon)
        assertEquals(3, success.data.stats.mvpCount)
        assertEquals(21, success.data.stats.totalGoals)
    }

    @Test
    fun `getPublicProfile returns not found when user does not exist`() = kotlinx.coroutines.runBlocking {
        val service = ProfileService(
            dbExecutor = FakeDbExecutor(),
            userRepository = FakeUserRepository(user = null),
            matchRepository = FakeMatchRepository(
                winStats = HomeWinStats(0, 0),
                mvpCount = 0,
                totalGoals = 0,
                lastMatch = null
            ),
            imageService = FakeImageService()
        )

        val result = service.getPublicProfile(UUID.randomUUID(), Locale.US, testContext)
        val failure = result as AppResult.Failure
        assertEquals(404, failure.status.value)
    }

    @Test
    fun `getPublicProfile returns lastMatch null when player has no completed matches`() = kotlinx.coroutines.runBlocking {
        val userId = UUID.randomUUID()
        val service = ProfileService(
            dbExecutor = FakeDbExecutor(),
            userRepository = FakeUserRepository(user = fakeUser(userId)),
            matchRepository = FakeMatchRepository(
                winStats = HomeWinStats(playedMatches = 0, wonMatches = 0),
                mvpCount = 0,
                totalGoals = 0,
                lastMatch = null
            ),
            imageService = FakeImageService()
        )

        val result = service.getPublicProfile(userId, Locale.US, testContext)
        val success = result as AppResult.Success
        assertNull(success.data.lastMatch)
    }

    @Test
    fun `getPublicProfile returns bad request for null userId`() = kotlinx.coroutines.runBlocking {
        val service = ProfileService(
            dbExecutor = FakeDbExecutor(),
            userRepository = FakeUserRepository(user = null),
            matchRepository = FakeMatchRepository(
                winStats = HomeWinStats(0, 0),
                mvpCount = 0,
                totalGoals = 0,
                lastMatch = null
            ),
            imageService = FakeImageService()
        )

        val result = service.getPublicProfile(null, Locale.US, testContext)
        val failure = result as AppResult.Failure
        assertEquals(404, failure.status.value)
    }

    @Test
    fun `updateManagedUserAccess revokes refresh tokens after blocking organizer`() = kotlinx.coroutines.runBlocking {
        val adminId = UUID.randomUUID()
        val organizerId = UUID.randomUUID()
        val repository = FakeUserRepository(user = fakeUser(organizerId).copy(userRole = UserRole.ORGANIZER))
        val refreshTokenRepository = FakeRefreshTokenRepository()
        val service = AdminUserService(
            dbExecutor = FakeDbExecutor(),
            userRepository = repository,
            refreshTokenRepository = refreshTokenRepository,
            imageService = FakeImageService()
        )

        val result = service.updateManagedUserAccess(
            adminId = adminId,
            targetUserId = organizerId,
            request = UpdateManagedUserAccessRequest(status = UserStatus.BLOCKED),
            locale = Locale.US,
            context = testContext
        )

        assertEquals(UserStatus.BLOCKED, repository.user?.status)
        assertEquals(organizerId, refreshTokenRepository.revokedUserId)
        val success = result as AppResult.Success
        assertEquals(200, success.status.value)
        assertEquals(true, success.data)
    }

    @Test
    fun `updateManagedUserAccess allows blocking another admin when another active admin remains`() = kotlinx.coroutines.runBlocking {
        val adminId = UUID.randomUUID()
        val targetAdminId = UUID.randomUUID()
        val repository = FakeUserRepository(user = fakeUser(targetAdminId).copy(userRole = UserRole.ADMIN))
        val service = AdminUserService(
            dbExecutor = FakeDbExecutor(),
            userRepository = repository,
            refreshTokenRepository = FakeRefreshTokenRepository(),
            imageService = FakeImageService()
        )

        val result = service.updateManagedUserAccess(
            adminId = adminId,
            targetUserId = targetAdminId,
            request = UpdateManagedUserAccessRequest(status = UserStatus.BLOCKED),
            locale = Locale.US,
            context = testContext
        )

        assertEquals(UserStatus.BLOCKED, repository.user?.status)
        assertEquals(true, (result as AppResult.Success).data)
    }

    @Test
    fun `updateManagedUserAccess prevents blocking the last active admin`() = kotlinx.coroutines.runBlocking {
        val adminId = UUID.randomUUID()
        val targetAdminId = UUID.randomUUID()
        val repository = FakeUserRepository(
            user = fakeUser(targetAdminId).copy(userRole = UserRole.ADMIN),
            activeAdminCount = 1
        )
        val service = AdminUserService(
            dbExecutor = FakeDbExecutor(),
            userRepository = repository,
            refreshTokenRepository = FakeRefreshTokenRepository(),
            imageService = FakeImageService()
        )

        val result = service.updateManagedUserAccess(
            adminId = adminId,
            targetUserId = targetAdminId,
            request = UpdateManagedUserAccessRequest(status = UserStatus.BLOCKED),
            locale = Locale.US,
            context = testContext
        )

        assertEquals(UserStatus.ACTIVE, repository.user?.status)
        assertEquals(403, (result as AppResult.Failure).status.value)
    }

    @Test
    fun `updateManagedUserAccess allows blocking a player`() = kotlinx.coroutines.runBlocking {
        val adminId = UUID.randomUUID()
        val playerId = UUID.randomUUID()
        val repository = FakeUserRepository(user = fakeUser(playerId))
        val service = AdminUserService(
            dbExecutor = FakeDbExecutor(),
            userRepository = repository,
            refreshTokenRepository = FakeRefreshTokenRepository(),
            imageService = FakeImageService()
        )

        val result = service.updateManagedUserAccess(
            adminId = adminId,
            targetUserId = playerId,
            request = UpdateManagedUserAccessRequest(status = UserStatus.BLOCKED),
            locale = Locale.US,
            context = testContext
        )

        assertEquals(UserStatus.BLOCKED, repository.user?.status)
        assertEquals(true, (result as AppResult.Success).data)
    }

    @Test
    fun `getManagedUsers forwards player and status filters to repository`() = kotlinx.coroutines.runBlocking {
        val repository = FakeUserRepository(user = null)
        val service = AdminUserService(
            dbExecutor = FakeDbExecutor(),
            userRepository = repository,
            refreshTokenRepository = FakeRefreshTokenRepository(),
            imageService = FakeImageService()
        )

        val result = service.getManagedUsers(
            page = 2,
            pageSize = 10,
            roleValues = listOf("PLAYER"),
            statusValues = listOf("ACTIVE,BLOCKED"),
            locale = Locale.US
        )

        assertEquals(setOf(UserRole.PLAYER), repository.requestedRoles)
        assertEquals(setOf(UserStatus.ACTIVE, UserStatus.BLOCKED), repository.requestedStatuses)
        assertEquals(2, (result as AppResult.Success).data.page)
    }

    private fun fakeUser(userId: UUID): UserBaseInfo {
        return UserBaseInfo(
            id = userId,
            name = "Carlos",
            lastName = "Perez",
            email = "test@example.com",
            phone = "123",
            status = UserStatus.ACTIVE,
            country = "Mexico",
            birthDate = 0L,
            gender = Gender.MALE,
            playerPosition = PlayerPosition.FORWARD,
            profilePic = "avatar.jpg",
            level = PlayerLevel.ADVANCED,
            userRole = UserRole.PLAYER,
            isEmailVerified = true,
            createdAt = 0L,
            updatedAt = 0L
        )
    }
}

private class FakeDbExecutor : DbExecutor {
    override suspend fun <T> tx(block: () -> T): T = block()
}

private class FakeImageService : ImageService {
    override suspend fun saveImages(multiPartData: MultiPartData, path: String) = error("not used")
    override suspend fun deleteImages(path: String) = error("not used")
    override fun getImageUrl(publicId: String): String = "https://cdn.example.com/$publicId"
}

private class FakeUserRepository(
    var user: UserBaseInfo?,
    private val activeAdminCount: Long = 2
) : UserRepository {
    var requestedRoles: Set<UserRole>? = null
    var requestedStatuses: Set<UserStatus>? = null
    override fun getUserById(userId: UUID): UserBaseInfo? = user

    override fun create(pendingUser: PendingUser): User = error("not used")
    override fun getHomeProfileById(userId: UUID): UserHomeProfile? = error("not used")
    override fun findByEmail(email: String): UserBaseInfo? = error("not used")
    override fun isEmailAlreadyRegistered(email: String): Boolean = error("not used")
    override suspend fun isPhoneNumberAlreadyRegistered(phone: String): Boolean = error("not used")
    override suspend fun isEmailVerified(userId: UUID): Boolean = error("not used")
    override fun getUserSignInInfo(email: String): UserSignInInfo? = error("not used")
    override fun updatePassword(userId: UUID, hashedPassword: String): Boolean = error("not used")
    override fun addUser(user: User): UUID = error("not used")
    override suspend fun updateUser(id: UUID, updatedUser: User): Boolean = error("not used")
    override suspend fun updateProfilePic(userId: UUID, fileName: String): Boolean = error("not used")
    override fun updateNameTx(userId: UUID, name: String, lastName: String): Boolean = error("not used")
    override fun updateCountryTx(userId: UUID, countryCode: String): Boolean = error("not used")
    override fun updateGenderTx(userId: UUID, gender: Gender): Boolean = error("not used")
    override fun updatePositionTx(userId: UUID, position: PlayerPosition): Boolean = error("not used")
    override fun markEmailAsVerified(userId: UUID): Boolean = error("not used")
    override suspend fun deleteUser(id: UUID): Boolean = error("not used")
    override suspend fun getPaymentProfile(userId: UUID, provider: PaymentProvider): String? = error("not used")
    override suspend fun upsertPaymentProfile(userId: UUID, provider: PaymentProvider, providerCustomerId: String): Boolean = error("not used")
    override fun getOrganizers(): List<OrganizerListItem> = error("not used")
    override fun getAdminManagedUsers(
        page: Int,
        pageSize: Int,
        roles: Set<UserRole>,
        statuses: Set<UserStatus>
    ): AdminManagedUsersPage {
        requestedRoles = roles
        requestedStatuses = statuses
        return AdminManagedUsersPage(items = emptyList(), total = 0)
    }
    override suspend fun getActiveAdminIds(): List<UUID> = error("not used")
    override fun countActiveAdminsTx(): Long = activeAdminCount
    override suspend fun getUserLocalesByIds(userIds: List<UUID>): Map<UUID, String> = error("not used")
    override fun updateManagedUserAccess(userId: UUID, role: UserRole, status: UserStatus): Boolean {
        val current = user ?: return false
        if (current.id != userId) return false
        user = current.copy(userRole = role, status = status)
        return true
    }
}

private class FakeRefreshTokenRepository : RefreshTokenRepository {
    var revokedUserId: UUID? = null

    override fun saveToken(userId: UUID, deviceId: UUID, token: String, expiresAt: Long): UUID = error("not used")
    override fun findByTokenHash(tokenHash: String): RefreshTokenRecord? = error("not used")
    override fun findValidationByTokenHash(tokenHash: String): RefreshTokenValidationRecord? = error("not used")
    override fun markTokenAsRotatedIfActive(tokenId: UUID, changedAt: Long): Boolean = error("not used")
    override fun markPreviousActiveTokensAsRotated(deviceId: UUID, currentTokenId: UUID, changedAt: Long): Boolean = error("not used")
    override fun updateTokenStatus(tokenId: UUID, status: RefreshTokenStatus, reason: RefreshTokenStatusReason, changedAt: Long): Boolean = error("not used")
    override fun revokeActiveTokens(deviceId: UUID, reason: RefreshTokenStatusReason, changedAt: Long): Boolean = error("not used")
    override fun revokeActiveTokensByUserId(userId: UUID, reason: RefreshTokenStatusReason, changedAt: Long): Int {
        revokedUserId = userId
        return 1
    }
    override suspend fun deleteRevokedTokens(): Boolean = error("not used")
}

private class FakeMatchRepository(
    private val winStats: HomeWinStats,
    private val mvpCount: Int,
    private val totalGoals: Int,
    private val lastMatch: HomeLastMatch?
) : MatchRepository {
    override suspend fun getHomeWinStats(userId: UUID): HomeWinStats = winStats
    override suspend fun getUserMvpCount(userId: UUID): Int = mvpCount
    override suspend fun getUserTotalGoals(userId: UUID): Int = totalGoals
    override suspend fun getHomeLastMatch(userId: UUID): HomeLastMatch? = lastMatch

    override suspend fun create(match: Match): MatchBaseInfo = error("not used")
    override suspend fun getMatchesByFieldId(fieldId: UUID): List<MatchWithFieldBaseInfo> = error("not used")
    override suspend fun getMatchesBySupervisorId(supervisorId: UUID): List<MatchWithFieldBaseInfo> = error("not used")
    override suspend fun getMatchTimeSlotsByFieldId(fieldId: UUID): List<MatchTimeSlot> = error("not used")
    override suspend fun getAllMatches(): List<MatchWithFieldBaseInfo> = error("not used")
    override suspend fun getUpcomingMatches(): List<MatchWithFieldBaseInfo> = error("not used")
    override suspend fun cancelMatch(matchId: UUID, reason: String): Boolean = error("not used")
    override suspend fun updateMatch(matchId: UUID, match: Match): Boolean = error("not used")
    override suspend fun getPublicMatches(): List<MatchWithField> = error("not used")
    override suspend fun getUserMatches(userId: UUID): List<MatchWithField> = error("not used")
    override suspend fun getMatchById(matchId: UUID): MatchWithField? = error("not used")
    override suspend fun addPlayerToMatch(matchId: UUID, userId: UUID, team: TeamType): Boolean = error("not used")
    override suspend fun removePlayerFromMatch(matchId: UUID, userId: UUID): Boolean = error("not used")
    override suspend fun isUserInMatch(matchId: UUID, userId: UUID): Boolean = error("not used")
    override suspend fun getMatchPlayerId(matchId: UUID, userId: UUID): UUID? = error("not used")
    override suspend fun updatePlayerStatus(matchPlayerId: UUID, status: MatchPlayerStatus): Boolean = error("not used")
    override suspend fun updatePlayerTeams(matchId: UUID, assignments: Map<UUID, TeamType>): Boolean = error("not used")
    override suspend fun getExpiredReservations(expirationTime: Long): List<ExpiredReservation> = error("not used")
    override suspend fun hasActiveReservation(userId: UUID): Boolean = error("not used")
    override suspend fun setPlayerGoals(matchId: UUID, goals: List<PlayerGoalInput>): Boolean = error("not used")
    override suspend fun setBestPlayer(matchId: UUID, bestPlayerId: UUID): Boolean = error("not used")
    override suspend fun completeMatchAtomic(
        matchId: UUID,
        bestPlayerId: UUID,
        goals: List<PlayerGoalInput>,
        externalGoals: List<TeamGoalInput>,
        absentPlayerIds: Set<UUID>
    ): Pair<Int, Int>? = error("not used")
    override suspend fun getMatchPlayerGoals(matchId: UUID): List<MatchPlayerGoal> = error("not used")
    override suspend fun calculateTeamScores(matchId: UUID): Pair<Int, Int> = error("not used")
    override suspend fun getHomeSuggestedMatches(userId: UUID, limit: Int): List<HomeSuggestedMatch> = emptyList()
    override suspend fun getMatchesPendingPaymentWindowWarning(startTimeWindow: Long, endTimeWindow: Long): List<MatchPaymentWindowWarningInfo> = error("not used")
    override suspend fun markPaymentWindowWarningSent(matchId: UUID, sentAt: Long): Boolean = error("not used")
}
