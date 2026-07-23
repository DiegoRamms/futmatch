package com.devapplab.di

import com.devapplab.data.repository.*
import com.devapplab.data.repository.auth.AuthRepository
import com.devapplab.data.repository.auth.AuthRepositoryImpl
import com.devapplab.data.repository.config.MatchPricingConfigRepository
import com.devapplab.data.repository.config.MatchPricingConfigRepositoryImpl
import com.devapplab.data.repository.cleanup.ProfileImageCleanupRepository
import com.devapplab.data.repository.cleanup.ProfileImageCleanupRepositoryImpl
import com.devapplab.data.repository.device.DeviceRepository
import com.devapplab.data.repository.device.DeviceRepositoryImpl
import com.devapplab.data.repository.discount.DiscountRepository
import com.devapplab.data.repository.discount.DiscountRepositoryImp
import com.devapplab.data.repository.location.LocationRepository
import com.devapplab.data.repository.location.LocationRepositoryImp
import com.devapplab.data.repository.login_attempt.LoginAttemptRepository
import com.devapplab.data.repository.login_attempt.LoginAttemptRepositoryImpl
import com.devapplab.data.repository.match.MatchRepository
import com.devapplab.data.repository.match.MatchRepositoryImp
import com.devapplab.data.repository.match.MatchRefundFailureRepository
import com.devapplab.data.repository.match.MatchRefundFailureRepositoryImpl
import com.devapplab.data.repository.match.PublicMatchesVersionRepository
import com.devapplab.data.repository.match.PublicMatchesVersionRepositoryImpl
import com.devapplab.data.repository.mfa.LoginMfaVerifyAttemptRepository
import com.devapplab.data.repository.mfa.LoginMfaChallengeRepository
import com.devapplab.data.repository.mfa.LoginMfaChallengeRepositoryImpl
import com.devapplab.data.repository.mfa.LoginMfaVerifyAttemptRepositoryImpl
import com.devapplab.data.repository.notification.NotificationRepository
import com.devapplab.data.repository.notification.NotificationRepositoryImpl
import com.devapplab.data.repository.password_reset.PasswordResetTokenRepository
import com.devapplab.data.repository.password_reset.PasswordResetTokenRepositoryImpl
import com.devapplab.data.repository.password_reset.PasswordResetVerifyAttemptRepository
import com.devapplab.data.repository.password_reset.PasswordResetVerifyAttemptRepositoryImpl
import com.devapplab.data.repository.payment.PaymentRepository
import com.devapplab.data.repository.payment.PaymentRepositoryImp
import com.devapplab.data.repository.payment.StripeWebhookEventRepository
import com.devapplab.data.repository.payment.StripeWebhookEventRepositoryImp
import com.devapplab.data.repository.pending_registrations.PendingRegistrationRepository
import com.devapplab.data.repository.pending_registrations.PendingRegistrationRepositoryImpl
import com.devapplab.data.repository.pending_registrations.RegistrationVerifyAttemptRepository
import com.devapplab.data.repository.pending_registrations.RegistrationVerifyAttemptRepositoryImpl
import com.devapplab.data.repository.user.UserRepository
import com.devapplab.data.repository.user.UserRepositoryImpl
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val repositoryModule = module {
    singleOf(::UserRepositoryImpl) { bind<UserRepository>() }
    singleOf(::RefreshTokenRepositoryImp) { bind<RefreshTokenRepository>() }
    singleOf(::DeviceRepositoryImpl) { bind<DeviceRepository>() }
    singleOf(::MfaCodeRepositoryImpl) { bind<MfaCodeRepository>() }
    singleOf(::LoginMfaChallengeRepositoryImpl) { bind<LoginMfaChallengeRepository>() }
    singleOf(::LoginMfaVerifyAttemptRepositoryImpl) { bind<LoginMfaVerifyAttemptRepository>() }
    singleOf(::AuthRepositoryImpl) { bind<AuthRepository>() }
    singleOf(::FieldRepositoryImp) { bind<FieldRepository>() }
    singleOf(::MatchRepositoryImp) { bind<MatchRepository>() }
    singleOf(::MatchRefundFailureRepositoryImpl) { bind<MatchRefundFailureRepository>() }
    singleOf(::PublicMatchesVersionRepositoryImpl) { bind<PublicMatchesVersionRepository>() }
    singleOf(::PasswordResetTokenRepositoryImpl) { bind<PasswordResetTokenRepository>() }
    singleOf(::PasswordResetVerifyAttemptRepositoryImpl) { bind<PasswordResetVerifyAttemptRepository>() }
    singleOf(::LoginAttemptRepositoryImpl) { bind<LoginAttemptRepository>() }
    singleOf(::PendingRegistrationRepositoryImpl) { bind<PendingRegistrationRepository>() }
    singleOf(::RegistrationVerifyAttemptRepositoryImpl) { bind<RegistrationVerifyAttemptRepository>() }
    singleOf(::LocationRepositoryImp) { bind<LocationRepository>() }
    singleOf(::DiscountRepositoryImp) { bind<DiscountRepository>() }
    singleOf(::PaymentRepositoryImp) { bind<PaymentRepository>() }
    singleOf(::StripeWebhookEventRepositoryImp){ bind<StripeWebhookEventRepository>() }
    singleOf(::NotificationRepositoryImpl) { bind<NotificationRepository>() }
    singleOf(::MatchPricingConfigRepositoryImpl) { bind<MatchPricingConfigRepository>() }
    singleOf(::ProfileImageCleanupRepositoryImpl) { bind<ProfileImageCleanupRepository>() }
}
