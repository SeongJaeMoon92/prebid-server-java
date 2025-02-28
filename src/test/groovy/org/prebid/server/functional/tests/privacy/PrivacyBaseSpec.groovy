package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.config.AccountCcpaConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountGdprConfig
import org.prebid.server.functional.model.config.AccountPrivacyConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.amp.ConsentType
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Device
import org.prebid.server.functional.model.request.auction.DistributionChannel
import org.prebid.server.functional.model.request.auction.Geo
import org.prebid.server.functional.model.request.auction.RegsExt
import org.prebid.server.functional.model.request.auction.User
import org.prebid.server.functional.model.request.auction.UserExt
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.tests.BaseSpec
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.privacy.ConsentString
import org.prebid.server.functional.util.privacy.TcfConsent
import spock.lang.Shared

import static org.prebid.server.functional.model.request.amp.ConsentType.GPP
import static org.prebid.server.functional.model.request.amp.ConsentType.TCF_2
import static org.prebid.server.functional.model.request.amp.ConsentType.US_PRIVACY
import static org.prebid.server.functional.model.request.auction.DistributionChannel.SITE
import static org.prebid.server.functional.util.privacy.TcfConsent.GENERIC_VENDOR_ID
import static org.prebid.server.functional.util.privacy.TcfConsent.PurposeId.BASIC_ADS

abstract class PrivacyBaseSpec extends BaseSpec {

    private static final int GEO_PRECISION = 2
    @Shared
    protected final PrebidServerService privacyPbsService = pbsServiceFactory.getService(
            ["adapters.generic.meta-info.vendor-id": GENERIC_VENDOR_ID as String])

    protected static BidRequest getBidRequestWithGeo(DistributionChannel channel = SITE) {
        BidRequest.getDefaultBidRequest(channel).tap {
            device = new Device(geo: new Geo(lat: PBSUtils.getRandomDecimal(0, 90), lon: PBSUtils.getRandomDecimal(0, 90)))
        }
    }

    protected static BidRequest getStoredRequestWithGeo() {
        BidRequest.defaultStoredRequest.tap {
            device = new Device(geo: new Geo(lat: PBSUtils.getRandomDecimal(0, 90), lon: PBSUtils.getRandomDecimal(0, 90)))
        }
    }

    protected static BidRequest getCcpaBidRequest(DistributionChannel channel = SITE, ConsentString consentString) {
        getBidRequestWithGeo(channel).tap {
            regs.ext = new RegsExt(usPrivacy: consentString)
        }
    }

    protected static AmpRequest getCcpaAmpRequest(ConsentString consentStringVal) {
        AmpRequest.defaultAmpRequest.tap {
            consentString = consentStringVal
            consentType = US_PRIVACY
        }
    }

    protected static BidRequest getGdprBidRequest(DistributionChannel channel = SITE, ConsentString consentString) {
        getBidRequestWithGeo(channel).tap {
            regs.ext = new RegsExt(gdpr: 1)
            user = new User(ext: new UserExt(consent: consentString))
        }
    }

    protected static AmpRequest getGdprAmpRequest(ConsentString consentString) {
        AmpRequest.defaultAmpRequest.tap {
            gdprConsent = consentString
            consentType = TCF_2
            gdprApplies = true
            timeout = 5000
        }
    }

    protected static AmpRequest getGppAmpRequest(String consentString,
                                                 String gppSid = null,
                                                 ConsentType consentType = GPP) {
        AmpRequest.defaultAmpRequest.tap {
            it.consentString = consentString
            it.gppSid = gppSid
            it.consentType = consentType
        }
    }

    protected static Geo maskGeo(BidRequest bidRequest, int precision = GEO_PRECISION) {
        def geo = bidRequest.device.geo.clone()
        geo.lat = PBSUtils.roundDecimal(bidRequest.device.geo.lat as BigDecimal, precision)
        geo.lon = PBSUtils.roundDecimal(bidRequest.device.geo.lon as BigDecimal, precision)
        geo
    }

    protected static void cacheVendorList(PrebidServerService pbsService) {
        def isVendorListCachedClosure = {
            def validConsentString = new TcfConsent.Builder()
                    .setPurposesLITransparency(BASIC_ADS)
                    .addVendorLegitimateInterest([GENERIC_VENDOR_ID])
                    .build()
            def bidRequest = getGdprBidRequest(validConsentString)

            pbsService.sendAuctionRequest(bidRequest)

            pbsService.sendCollectedMetricsRequest()["privacy.tcf.v2.vendorlist.missing"] == 0
        }
        PBSUtils.waitUntil(isVendorListCachedClosure, 10000, 1000)
    }

    protected static Account getAccountWithGdpr(String accountId, AccountGdprConfig gdprConfig) {
        getAccountWithPrivacy(accountId, new AccountPrivacyConfig(gdpr: gdprConfig))
    }

    protected static Account getAccountWithCcpa(String accountId, AccountCcpaConfig ccpaConfig) {
        getAccountWithPrivacy(accountId, new AccountPrivacyConfig(ccpa: ccpaConfig))
    }

    private static Account getAccountWithPrivacy(String accountId, AccountPrivacyConfig privacy) {
        new Account(uuid: accountId, config: new AccountConfig(privacy: privacy))
    }
}
