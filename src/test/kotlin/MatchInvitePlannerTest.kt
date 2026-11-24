import com.devapplab.model.MatchInviteConfig
import com.devapplab.model.match.GenderType
import com.devapplab.model.match.MatchInviteCandidate
import com.devapplab.model.match.MatchInviteInfo
import com.devapplab.model.match.MatchInviteUserEligibility
import com.devapplab.model.match.MatchStatus
import com.devapplab.model.user.Gender
import com.devapplab.model.user.PlayerLevel
import com.devapplab.model.user.UserRole
import com.devapplab.model.user.UserStatus
import com.devapplab.service.match.MatchInvitePlanner
import com.devapplab.utils.StringResourcesKey
import com.devapplab.utils.getString
import java.util.Locale
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class MatchInvitePlannerTest {
    private val now = 1_800_000_000_000L
    private val milestones = listOf(24, 6, 2)
    private fun inHours(h: Double) = now + (h * 60).toLong().minutes.inWholeMilliseconds

    @Test
    fun `nothing is due more than 24h before kick-off`() {
        assertTrue(MatchInvitePlanner.dueMilestones(inHours(30.0), now, milestones, emptySet()).isEmpty())
    }

    @Test
    fun `24h milestone is due inside the first window`() {
        assertEquals(setOf(24), MatchInvitePlanner.dueMilestones(inHours(23.5), now, milestones, emptySet()))
    }

    @Test
    fun `match created 90 minutes before consumes every milestone in one send`() {
        assertEquals(setOf(24, 6, 2), MatchInvitePlanner.dueMilestones(inHours(1.5), now, milestones, emptySet()))
    }

    @Test
    fun `already dispatched milestones are skipped`() {
        assertEquals(setOf(6), MatchInvitePlanner.dueMilestones(inHours(5.0), now, milestones, setOf(24)))
        assertTrue(MatchInvitePlanner.dueMilestones(inHours(1.0), now, milestones, setOf(24, 6, 2)).isEmpty())
    }

    @Test
    fun `48h milestone goes out first, then 24h`() {
        val withTwoDays = listOf(48, 24, 6, 2)
        assertTrue(MatchInvitePlanner.dueMilestones(inHours(50.0), now, withTwoDays, emptySet()).isEmpty())
        assertEquals(setOf(48), MatchInvitePlanner.dueMilestones(inHours(47.9), now, withTwoDays, emptySet()))
        assertTrue(MatchInvitePlanner.dueMilestones(inHours(30.0), now, withTwoDays, setOf(48)).isEmpty())
        assertEquals(setOf(24), MatchInvitePlanner.dueMilestones(inHours(23.9), now, withTwoDays, setOf(48)))
    }

    @Test
    fun `a newly added larger milestone never fires after a smaller one was sent`() {
        val withTwoDays = listOf(48, 24, 6, 2)
        assertTrue(MatchInvitePlanner.dueMilestones(inHours(10.0), now, withTwoDays, setOf(24)).isEmpty())
        assertEquals(setOf(6), MatchInvitePlanner.dueMilestones(inHours(5.0), now, withTwoDays, setOf(24)))
    }

    @Test
    fun `started matches never get invites`() {
        assertTrue(MatchInvitePlanner.dueMilestones(now - 1.hours.inWholeMilliseconds, now, milestones, emptySet()).isEmpty())
    }

    @Test
    fun `each user gets at most one invite, for the soonest match`() {
        val later = match(dateTime = inHours(20.0))
        val sooner = match(dateTime = inHours(5.0))
        val user = candidate()
        val result = MatchInvitePlanner.assignInvites(listOf(later, sooner), listOf(user), emptyMap(), emptySet())
        assertEquals(mapOf(sooner.matchId to listOf(user)), result)
    }

    @Test
    fun `ties go to the match with fewer spots`() {
        val roomy = match(dateTime = inHours(5.0), enrolled = 2)
        val almostFull = match(dateTime = inHours(5.0), enrolled = 9)
        val user = candidate()
        val result = MatchInvitePlanner.assignInvites(listOf(roomy, almostFull), listOf(user), emptyMap(), emptySet())
        assertEquals(setOf(almostFull.matchId), result.keys)
    }

    @Test
    fun `players already in the match fall through to the next one`() {
        val sooner = match(dateTime = inHours(5.0))
        val later = match(dateTime = inHours(20.0))
        val user = candidate()
        val result = MatchInvitePlanner.assignInvites(
            listOf(sooner, later), listOf(user), mapOf(sooner.matchId to setOf(user.userId)), emptySet()
        )
        assertEquals(setOf(later.matchId), result.keys)
    }

    @Test
    fun `daily cap, organizers, full matches and visibility are excluded`() {
        val admin = candidate()
        val invitedToday = candidate()
        val woman = candidate(gender = Gender.FEMALE)
        val beginner = candidate(level = PlayerLevel.BEGINNER)
        val maleAdvanced = match(dateTime = inHours(5.0), adminId = admin.userId, genderType = GenderType.MALE_ONLY, level = PlayerLevel.ADVANCED)
        val full = match(dateTime = inHours(3.0), enrolled = 10)

        val result = MatchInvitePlanner.assignInvites(
            listOf(maleAdvanced, full),
            listOf(admin, invitedToday, woman, beginner),
            emptyMap(),
            setOf(invitedToday.userId)
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `milestone config is parsed, sorted and falls back to defaults`() {
        assertEquals(listOf(12, 3), MatchInviteConfig.parseMilestones(" 3, 12,x,0,3"))
        assertEquals(listOf(48, 24, 6, 2), MatchInviteConfig.parseMilestones(""))
        assertEquals(listOf(48, 24, 6, 2), MatchInviteConfig.parseMilestones(null))
    }

    @Test
    fun `diagnosis passes for an eligible player`() {
        val m = match(inHours(47.0))
        val checks = diagnose(m, eligibility())
        assertTrue(checks.all { it.passed }, checks.filterNot { it.passed }.toString())
    }

    @Test
    fun `diagnosis names each reason a user is excluded`() {
        val m = match(inHours(47.0), genderType = GenderType.FEMALE_ONLY)
        val user = eligibility(
            userId = m.adminId,
            role = UserRole.ORGANIZER,
            hasPushToken = false,
            lastInviteAt = now - 2.hours.inWholeMilliseconds,
        )
        val failed = diagnose(m, user, activePlayers = setOf(m.adminId)).filterNot { it.passed }.map { it.name }
        assertEquals(
            listOf(
                "role_allowed", "has_push_token", "not_match_admin", "not_in_match",
                "gender_and_level_visible", "no_invite_last_24h"
            ),
            failed
        )
    }

    @Test
    fun `an admin who doesn't run the match is eligible`() {
        val checks = diagnose(match(inHours(47.0)), eligibility(role = UserRole.ADMIN))
        assertTrue(checks.all { it.passed }, checks.filterNot { it.passed }.toString())
    }

    @Test
    fun `diagnosis flags the match side and a missing user`() {
        val m = match(inHours(10.0), enrolled = 10)
        val failed = diagnose(m, null, status = MatchStatus.CANCELED, dispatched = setOf(24, 6))
            .filterNot { it.passed }.map { it.name }
        assertEquals(listOf("match_scheduled", "match_has_spots", "milestone_pending", "user_found"), failed)
        assertEquals(listOf(2), MatchInvitePlanner.pendingMilestones(listOf(48, 24, 6, 2), setOf(24, 6)))
        assertTrue(MatchInvitePlanner.pendingMilestones(listOf(48, 24, 6, 2), setOf(2)).isEmpty())
    }

    private fun diagnose(
        m: MatchInviteInfo,
        user: MatchInviteUserEligibility?,
        activePlayers: Set<UUID> = emptySet(),
        status: MatchStatus = MatchStatus.SCHEDULED,
        dispatched: Set<Int> = emptySet(),
    ) = MatchInvitePlanner.diagnose(
        match = m,
        matchStatus = status,
        user = user,
        activePlayers = activePlayers,
        milestonesHours = listOf(48, 24, 6),
        dispatched = dispatched,
        now = now,
        dailyWindowMs = 24.hours.inWholeMilliseconds,
    )

    private fun eligibility(
        userId: UUID = UUID.randomUUID(),
        role: UserRole = UserRole.PLAYER,
        hasPushToken: Boolean = true,
        lastInviteAt: Long? = null,
    ) = MatchInviteUserEligibility(
        userId = userId,
        role = role,
        status = UserStatus.ACTIVE,
        notificationsEnabled = true,
        matchInvitesEnabled = true,
        hasPushToken = hasPushToken,
        gender = Gender.MALE,
        level = PlayerLevel.ANY,
        lastInviteAt = lastInviteAt,
    )

    @Test
    fun `invite strings resolve with accents and placeholders in both languages`() {
        val placeholders = mapOf("spots" to "3", "fieldName" to "Cancha Central", "date" to "hoy")
        val es = Locale.forLanguageTag("es-MX")
        assertEquals("\u00a1Se busca jugador! \u26bd", es.getString(StringResourcesKey.NOTIFICATION_MATCH_INVITE_TITLE))
        assertEquals(
            "Quedan 3 lugares en Cancha Central (hoy). \u00a1\u00danete antes de que se llene!",
            es.getString(StringResourcesKey.NOTIFICATION_MATCH_INVITE_BODY_MANY, placeholders)
        )
        assertTrue(es.getString(StringResourcesKey.NOTIFICATION_MATCH_INVITE_BODY_ONE, placeholders).startsWith("Queda 1 lugar"))
        assertEquals(
            "3 spots left at Cancha Central (hoy). Join before it fills up!",
            Locale.forLanguageTag("en-US").getString(StringResourcesKey.NOTIFICATION_MATCH_INVITE_BODY_MANY, placeholders)
        )
    }

    private fun match(
        dateTime: Long,
        enrolled: Int = 4,
        adminId: UUID = UUID.randomUUID(),
        genderType: GenderType = GenderType.MIXED,
        level: PlayerLevel = PlayerLevel.ANY,
    ) = MatchInviteInfo(
        matchId = UUID.randomUUID(),
        fieldName = "Cancha",
        dateTime = dateTime,
        maxPlayers = 10,
        enrolledPlayers = enrolled,
        genderType = genderType,
        playerLevel = level,
        adminId = adminId,
        supervisorId = null,
    )

    private fun candidate(gender: Gender = Gender.MALE, level: PlayerLevel = PlayerLevel.ANY) =
        MatchInviteCandidate(UUID.randomUUID(), "es-MX", gender, level)
}
