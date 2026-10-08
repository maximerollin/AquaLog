package com.maximerollin.aqualog

import android.app.Application
import android.app.Activity
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import com.maximerollin.aqualog.shared.AquaLogDatabase
import com.maximerollin.aqualog.shared.AquariumRepository
import com.maximerollin.aqualog.shared.RoomAquariumRepository
import com.maximerollin.aqualog.shared.ANNUAL_PRO_PRODUCT_ID
import com.maximerollin.aqualog.shared.AnnualProOffer
import com.maximerollin.aqualog.shared.BillingLaunchResult
import com.maximerollin.aqualog.shared.PlayPurchaseUpdate
import com.maximerollin.aqualog.shared.ProBillingGateway
import com.maximerollin.aqualog.shared.ProPurchaseCoordinator
import com.maximerollin.aqualog.shared.ProPurchaseVerifier
import com.maximerollin.aqualog.shared.PurchaseVerificationResult
import com.maximerollin.aqualog.shared.createAndroidDatabaseBuilder
import com.maximerollin.aqualog.shared.createAquaLogDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.util.UUID

class AquaLogTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application = super.newApplication(cl, TestAquaLogApplication::class.java.name, context)
}

class TestAquaLogApplication : AquaLogApplication() {
    private var database: AquaLogDatabase? = null
    private var repository: AquariumRepository? = null
    private var databaseSequence = 0

    override val aquariumRepository: AquariumRepository
        get() = requireNotNull(repository) { "resetRepository() must run before launching MainActivity" }

    override fun proPurchaseCoordinator(activity: Activity): ProPurchaseCoordinator = ProPurchaseCoordinator(
        billingGateway = object : ProBillingGateway {
            override val purchaseUpdates: Flow<PlayPurchaseUpdate> = emptyFlow()
            override suspend fun loadAnnualOffer() = AnnualProOffer(
                productId = ANNUAL_PRO_PRODUCT_ID,
                formattedPrice = "€14.99",
                trialDays = 14,
                formattedBillingDate = "October 21, 2026",
            )
            override suspend fun launchAnnualPurchase() = BillingLaunchResult.Started
            override suspend fun restoreAnnualPurchase(): PlayPurchaseUpdate? = null
        },
        purchaseVerifier = object : ProPurchaseVerifier {
            override suspend fun verify(productId: String, purchaseToken: String) =
                PurchaseVerificationResult.Rejected("Test purchases require an explicit test")
        },
    )

    fun resetRepository() {
        database?.close()
        val databaseName = "aqualog-test-${databaseSequence++}.db"
        deleteDatabase(databaseName)
        val newDatabase = createAquaLogDatabase(createAndroidDatabaseBuilder(this, databaseName))
        database = newDatabase
        repository = RoomAquariumRepository(
            database = newDatabase,
            generateId = { UUID.randomUUID().toString() },
            currentTimeMillis = System::currentTimeMillis,
        )
    }

    override fun onTerminate() {
        database?.close()
        super.onTerminate()
    }
}
