package com.unity3d.ads.android;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.json.JSONObject;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

import com.unity3d.ads.android.cache.IUnityAdsCacheListener;
import com.unity3d.ads.android.cache.UnityAdsCacheManager;
import com.unity3d.ads.android.cache.UnityAdsDownloader;
import com.unity3d.ads.android.campaign.UnityAdsCampaign;
import com.unity3d.ads.android.campaign.UnityAdsCampaign.UnityAdsCampaignStatus;
import com.unity3d.ads.android.campaign.UnityAdsCampaignHandler;
import com.unity3d.ads.android.data.UnityAdsAdvertisingId;
import com.unity3d.ads.android.item.UnityAdsRewardItem;
import com.unity3d.ads.android.item.UnityAdsRewardItemManager;
import com.unity3d.ads.android.properties.UnityAdsConstants;
import com.unity3d.ads.android.properties.UnityAdsProperties;
import com.unity3d.ads.android.view.IUnityAdsMainViewListener;
import com.unity3d.ads.android.view.UnityAdsFullscreenActivity;
import com.unity3d.ads.android.view.UnityAdsMainView;
import com.unity3d.ads.android.view.UnityAdsMainView.UnityAdsMainViewAction;
import com.unity3d.ads.android.view.UnityAdsMainView.UnityAdsMainViewState;
import com.unity3d.ads.android.webapp.IUnityAdsWebBridgeListener;
import com.unity3d.ads.android.webapp.IUnityAdsWebDataListener;
import com.unity3d.ads.android.webapp.UnityAdsWebData;
import com.unity3d.ads.android.zone.UnityAdsIncentivizedZone;
import com.unity3d.ads.android.zone.UnityAdsZone;


@TargetApi(Build.VERSION_CODES.GINGERBREAD)
public class UnityAds implements IUnityAdsCacheListener,
										IUnityAdsWebDataListener,
										IUnityAdsWebBridgeListener,
										IUnityAdsMainViewListener {

	// Reward item HashMap keys
	public static final String UNITY_ADS_REWARDITEM_PICTURE_KEY = "picture";
	public static final String UNITY_ADS_REWARDITEM_NAME_KEY = "name";

	// Unity Ads developer options keys
	public static final String UNITY_ADS_OPTION_NOOFFERSCREEN_KEY = "noOfferScreen";
	public static final String UNITY_ADS_OPTION_OPENANIMATED_KEY = "openAnimated";
	public static final String UNITY_ADS_OPTION_GAMERSID_KEY = "sid";
	public static final String UNITY_ADS_OPTION_MUTE_VIDEO_SOUNDS = "muteVideoSounds";
	public static final String UNITY_ADS_OPTION_VIDEO_USES_DEVICE_ORIENTATION = "useDeviceOrientationForVideo";

	// Unity Ads components
	public static UnityAdsCacheManager cachemanager = null;
	public static UnityAdsWebData webdata = null;
	public static UnityAdsMainView mainview = null;

	// Temporary data
	private static boolean _initialized = false;
	private static boolean _showingAds = false;
	private static boolean _adsReadySent = false;
	private static boolean _openRequestFromDeveloper = false;
	private static boolean _refreshAfterShowAds = false;
	private static AlertDialog _alertDialog = null;

	private static TimerTask _pauseScreenTimer = null;
	private static Timer _pauseTimer = null;
	private static TimerTask _campaignRefreshTimerTask = null;
	private static Timer _campaignRefreshTimer = null;
	private static long _campaignRefreshTimerDeadline = 0;

	private static UnityAds _instance = null;

	// Listeners
	private static IUnityAdsListener _adsListener = null;

	private UnityAds () {
	}

	/* PUBLIC STATIC METHODS */

	public static boolean isSupported () {
		if (Build.VERSION.SDK_INT < 9) {
			return false;
		}

		return true;
	}

	public static void setDebugMode (boolean debugModeEnabled) {
		UnityAdsProperties.UNITY_ADS_DEBUG_MODE = debugModeEnabled;
	}

	public static void setTestMode (boolean testModeEnabled) {
		UnityAdsProperties.TESTMODE_ENABLED = testModeEnabled;
	}

	public static void setTestDeveloperId (String testDeveloperId) {
		UnityAdsProperties.TEST_DEVELOPER_ID = testDeveloperId;
	}

	public static void setTestOptionsId (String testOptionsId) {
		UnityAdsProperties.TEST_OPTIONS_ID = testOptionsId;
	}

	public static String getSDKVersion () {
		return UnityAdsConstants.UNITY_ADS_VERSION;
	}


	/* PUBLIC METHODS */

	public static void setListener (IUnityAdsListener listener) {
		_adsListener = listener;
	}

	public static void changeActivity (Activity activity) {
		if (activity == null) return;

		if (activity != null && !activity.equals(UnityAdsProperties.getCurrentActivity())) {
			UnityAdsProperties.CURRENT_ACTIVITY = new WeakReference<Activity>(activity);

			// Not the most pretty way to detect when the fullscreen activity is ready
			if (activity != null && activity instanceof UnityAdsFullscreenActivity) {
				String view = null;

				if (_openRequestFromDeveloper) {
					view = UnityAdsConstants.UNITY_ADS_WEBVIEW_VIEWTYPE_START;
					UnityAdsDeviceLog.debug("This open request is from the developer, setting start view");
				}

				if (view != null) {
					setupViews();
					open(view);
				}

				_openRequestFromDeveloper = false;
			}
			else {
				UnityAdsProperties.BASE_ACTIVITY = new WeakReference<Activity>(activity);
			}
		}
	}

	public static boolean hide () {
		if (_showingAds) {
			close();
			return true;
		}

		return false;
	}

	public static boolean setZone(String zoneId) {
		if(!_showingAds) {
			if(UnityAdsWebData.getZoneManager() == null) {
				throw new IllegalStateException("Unable to set zone before campaigns are available");
			}

			return UnityAdsWebData.getZoneManager().setCurrentZone(zoneId);
		}
		return false;
	}

	public static boolean setZone(String zoneId, String rewardItemKey) {
		if(!_showingAds && setZone(zoneId)) {
			UnityAdsZone currentZone = UnityAdsWebData.getZoneManager().getCurrentZone();
			if(currentZone.isIncentivized()) {
				UnityAdsRewardItemManager itemManager = ((UnityAdsIncentivizedZone)currentZone).itemManager();
				return itemManager.setCurrentItem(rewardItemKey);
			}
		}
		return false;
	}

	public static boolean show (Map<String, Object> options) {
		if (canShow()) {
			UnityAdsZone currentZone = UnityAdsWebData.getZoneManager().getCurrentZone();

			if (currentZone != null) {
				UnityAdsDownloader.stopAllDownloads();

				currentZone.mergeOptions(options);

				if (currentZone.noOfferScreen()) {
					ArrayList<UnityAdsCampaign> viewableCampaigns = webdata.getViewableVideoPlanCampaigns();

					if (viewableCampaigns.size() > 0) {
						UnityAdsCampaign selectedCampaign = viewableCampaigns.get(0);
						UnityAdsProperties.SELECTED_CAMPAIGN = selectedCampaign;

						if(viewableCampaigns.size() > 1) {
							UnityAdsCampaign nextCampaign = viewableCampaigns.get(1);

							if(cachemanager.isCampaignCached(selectedCampaign) && !cachemanager.isCampaignCached(nextCampaign) && nextCampaign.allowCacheVideo()) {
								cachemanager.cacheNextVideo(nextCampaign);
							}
						}
					}
				}

				_openRequestFromDeveloper = true;
				_showingAds = true;
				startFullscreenActivity();
				return _showingAds;
			}

		}

		return false;
	}

	public static boolean show () {
		return show(null);
	}

	public static boolean canShowAds () {
			return webdata != null &&
			webdata.getViewableVideoPlanCampaigns() != null &&
			webdata.getViewableVideoPlanCampaigns().size() > 0;
	}

	public static boolean canShow () {
		return !_showingAds &&
			webdata != null &&
			webdata.getVideoPlanCampaigns() != null &&
			webdata.getVideoPlanCampaigns().size() > 0;
	}

	/* PUBLIC MULTIPLE REWARD ITEM SUPPORT */

	public static boolean hasMultipleRewardItems () {
		UnityAdsZone zone = UnityAdsWebData.getZoneManager().getCurrentZone();
		if(zone.isIncentivized()) {
			UnityAdsRewardItemManager itemManager = ((UnityAdsIncentivizedZone)zone).itemManager();
			return itemManager.itemCount() > 1;
		}
		return false;
	}

	public static ArrayList<String> getRewardItemKeys () {
		UnityAdsZone zone = UnityAdsWebData.getZoneManager().getCurrentZone();
		if(zone.isIncentivized()) {
			UnityAdsRewardItemManager itemManager = ((UnityAdsIncentivizedZone)zone).itemManager();
			ArrayList<UnityAdsRewardItem> rewardItems = itemManager.allItems();
			ArrayList<String> rewardItemKeys = new ArrayList<String>();
			for (UnityAdsRewardItem rewardItem : rewardItems) {
				rewardItemKeys.add(rewardItem.getKey());
			}

			return rewardItemKeys;
		}
		return null;
	}

	public static String getDefaultRewardItemKey () {
		UnityAdsZone zone = UnityAdsWebData.getZoneManager().getCurrentZone();
		if(zone.isIncentivized()) {
			UnityAdsRewardItemManager itemManager = ((UnityAdsIncentivizedZone)zone).itemManager();
			return itemManager.getDefaultItem().getKey();
		}
		return null;
	}

	public static String getCurrentRewardItemKey () {
		UnityAdsZone zone = UnityAdsWebData.getZoneManager().getCurrentZone();
		if(zone.isIncentivized()) {
			UnityAdsRewardItemManager itemManager = ((UnityAdsIncentivizedZone)zone).itemManager();
			return itemManager.getCurrentItem().getKey();
		}
		return null;
	}

	public static boolean setRewardItemKey (String rewardItemKey) {
		if (canShow()) {
			UnityAdsZone zone = UnityAdsWebData.getZoneManager().getCurrentZone();
			if(zone.isIncentivized()) {
				UnityAdsRewardItemManager itemManager = ((UnityAdsIncentivizedZone)zone).itemManager();
				return itemManager.setCurrentItem(rewardItemKey);
			}
		}
		return false;
	}

	public static void setDefaultRewardItemAsRewardItem () {
		if (canShow()) {
			UnityAdsZone zone = UnityAdsWebData.getZoneManager().getCurrentZone();
			if(zone.isIncentivized()) {
				UnityAdsRewardItemManager itemManager = ((UnityAdsIncentivizedZone)zone).itemManager();
				itemManager.setCurrentItem(itemManager.getDefaultItem().getKey());
			}
		}
	}

	public static Map<String, String> getRewardItemDetailsWithKey (String rewardItemKey) {
		UnityAdsZone zone = UnityAdsWebData.getZoneManager().getCurrentZone();
		if(zone.isIncentivized()) {
			UnityAdsRewardItemManager itemManager = ((UnityAdsIncentivizedZone)zone).itemManager();
			UnityAdsRewardItem rewardItem = itemManager.getItem(rewardItemKey);
			if (rewardItem != null) {
				return rewardItem.getDetails();
			}
			else {
				UnityAdsDeviceLog.info("Could not fetch reward item: " + rewardItemKey);
			}
		}
		return null;
	}


	/* LISTENER METHODS */

	// IUnityAdsMainViewListener
	@Override
	public void onMainViewAction (UnityAdsMainViewAction action) {
		switch (action) {
			case BackButtonPressed:
				if (_showingAds) {
					close();
				}
				break;
			case VideoStart:
				if (_adsListener != null)
					_adsListener.onVideoStarted();
				cancelPauseScreenTimer();
				break;
			case VideoEnd:
				UnityAdsProperties.CAMPAIGN_REFRESH_VIEWS_COUNT++;
				if (_adsListener != null && UnityAdsProperties.SELECTED_CAMPAIGN != null && !UnityAdsProperties.SELECTED_CAMPAIGN.isViewed()) {
					UnityAdsProperties.SELECTED_CAMPAIGN.setCampaignStatus(UnityAdsCampaignStatus.VIEWED);
					_adsListener.onVideoCompleted(getCurrentRewardItemKey(), false);
				}
				break;
			case VideoSkipped:
				UnityAdsProperties.CAMPAIGN_REFRESH_VIEWS_COUNT++;
				if (_adsListener != null && UnityAdsProperties.SELECTED_CAMPAIGN != null && !UnityAdsProperties.SELECTED_CAMPAIGN.isViewed()) {
					UnityAdsProperties.SELECTED_CAMPAIGN.setCampaignStatus(UnityAdsCampaignStatus.VIEWED);
					_adsListener.onVideoCompleted(getCurrentRewardItemKey(), true);
				}
				break;
			case RequestRetryVideoPlay:
				UnityAdsDeviceLog.debug("Retrying video play, because something went wrong.");
				playVideo(300);
				break;
		}
	}

	// IUnityAdsCacheListener
	@Override
	public void onCampaignUpdateStarted () {
		UnityAdsDeviceLog.debug("Campaign updates started.");
	}

	@Override
	public void onCampaignReady (UnityAdsCampaignHandler campaignHandler) {
		if (campaignHandler == null || campaignHandler.getCampaign() == null) return;

		UnityAdsDeviceLog.debug(campaignHandler.getCampaign().toString());

		if (canShowAds())
			sendReadyEvent();
	}

	@Override
	public void onAllCampaignsReady () {
		UnityAdsDeviceLog.entered();
	}

	// IUnityAdsWebDataListener
	@SuppressWarnings("deprecation")
	@Override
	public void onWebDataCompleted () {
		UnityAdsDeviceLog.entered();
		JSONObject jsonData = null;
		boolean dataFetchFailed = false;
		boolean sdkIsCurrent = true;

		if (webdata.getData() != null && webdata.getData().has(UnityAdsConstants.UNITY_ADS_JSON_DATA_ROOTKEY)) {
			try {
				jsonData = webdata.getData().getJSONObject(UnityAdsConstants.UNITY_ADS_JSON_DATA_ROOTKEY);
			}
			catch (Exception e) {
				dataFetchFailed = true;
			}

			if (!dataFetchFailed) {
				setupCampaignRefreshTimer();

				if (jsonData.has(UnityAdsConstants.UNITY_ADS_WEBVIEW_DATAPARAM_SDK_IS_CURRENT_KEY)) {
					try {
						sdkIsCurrent = jsonData.getBoolean(UnityAdsConstants.UNITY_ADS_WEBVIEW_DATAPARAM_SDK_IS_CURRENT_KEY);
					}
					catch (Exception e) {
						dataFetchFailed = true;
					}
				}
			}
		}

		if (!dataFetchFailed && !sdkIsCurrent && UnityAdsUtils.isDebuggable(UnityAdsProperties.getCurrentActivity())) {
			_alertDialog = new AlertDialog.Builder(UnityAdsProperties.getCurrentActivity()).create();
			_alertDialog.setTitle("Unity Ads");
			_alertDialog.setMessage("You are not running the latest version of Unity Ads android. Please update your version (this dialog won't appear in release builds).");
			_alertDialog.setButton("OK", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					_alertDialog.dismiss();
				}
			});

			_alertDialog.show();
		}

		setup();
	}

	@Override
	public void onWebDataFailed () {
		if (_adsListener != null && !_adsReadySent)
			_adsListener.onFetchFailed();
	}


	// IUnityAdsWebBrigeListener
	@Override
	public void onPlayVideo(JSONObject data) {
		UnityAdsDeviceLog.entered();
		if (data.has(UnityAdsConstants.UNITY_ADS_WEBVIEW_EVENTDATA_CAMPAIGNID_KEY)) {
			String campaignId = null;

			try {
				campaignId = data.getString(UnityAdsConstants.UNITY_ADS_WEBVIEW_EVENTDATA_CAMPAIGNID_KEY);
			}
			catch (Exception e) {
				UnityAdsDeviceLog.error("Could not get campaignId");
			}

			if (campaignId != null) {
				if (webdata != null && webdata.getCampaignById(campaignId) != null) {
					UnityAdsProperties.SELECTED_CAMPAIGN = webdata.getCampaignById(campaignId);
				}

				if (UnityAdsProperties.SELECTED_CAMPAIGN != null &&
					UnityAdsProperties.SELECTED_CAMPAIGN.getCampaignId() != null &&
					UnityAdsProperties.SELECTED_CAMPAIGN.getCampaignId().equals(campaignId)) {

					Boolean rewatch = false;

					try {
						rewatch = data.getBoolean(UnityAdsConstants.UNITY_ADS_WEBVIEW_EVENTDATA_REWATCH_KEY);
					}
					catch (Exception e) {
					}

					UnityAdsDeviceLog.debug("Selected campaign=" + UnityAdsProperties.SELECTED_CAMPAIGN.getCampaignId() + " isViewed: " + UnityAdsProperties.SELECTED_CAMPAIGN.isViewed());
					if (UnityAdsProperties.SELECTED_CAMPAIGN != null && (rewatch || !UnityAdsProperties.SELECTED_CAMPAIGN.isViewed())) {
						playVideo();
					}
				}
			}
		}
	}

	@Override
	public void onPauseVideo(JSONObject data) {
	}

	@Override
	public void onCloseAdsView(JSONObject data) {
		hide();
	}

	@Override
	public void onWebAppInitComplete (JSONObject data) {
		UnityAdsDeviceLog.entered();
		Boolean dataOk = true;

		if (canShowAds()) {
			JSONObject setViewData = new JSONObject();

			try {
				setViewData.put(UnityAdsConstants.UNITY_ADS_WEBVIEW_API_ACTION_KEY, UnityAdsConstants.UNITY_ADS_WEBVIEW_API_INITCOMPLETE);
			}
			catch (Exception e) {
				dataOk = false;
			}

			if (dataOk && mainview != null && mainview.webview != null) {
				mainview.webview.setWebViewCurrentView(UnityAdsConstants.UNITY_ADS_WEBVIEW_VIEWTYPE_START, setViewData);
				sendReadyEvent();
			}
		}
	}

	@Override
	public void onOpenPlayStore (JSONObject data) {
		UnityAdsDeviceLog.entered();

	    if (data != null) {

	    	UnityAdsDeviceLog.debug(data.toString());

	    	String playStoreId = null;
	    	String clickUrl = null;
	    	Boolean bypassAppSheet = false;

	    	if (data.has(UnityAdsConstants.UNITY_ADS_PLAYSTORE_ITUNESID_KEY)) {
	    		try {
		    		playStoreId = data.getString(UnityAdsConstants.UNITY_ADS_PLAYSTORE_ITUNESID_KEY);
	    		}
	    		catch (Exception e) {
	    			UnityAdsDeviceLog.error("Could not fetch playStoreId");
	    		}
	    	}

	    	if (data.has(UnityAdsConstants.UNITY_ADS_PLAYSTORE_CLICKURL_KEY)) {
	    		try {
	    			clickUrl = data.getString(UnityAdsConstants.UNITY_ADS_PLAYSTORE_CLICKURL_KEY);
	    		}
	    		catch (Exception e) {
	    			UnityAdsDeviceLog.error("Could not fetch clickUrl");
	    		}
	    	}

	    	if (data.has(UnityAdsConstants.UNITY_ADS_PLAYSTORE_BYPASSAPPSHEET_KEY)) {
	    		try {
	    			bypassAppSheet = data.getBoolean(UnityAdsConstants.UNITY_ADS_PLAYSTORE_BYPASSAPPSHEET_KEY);
	    		}
	    		catch (Exception e) {
	    			UnityAdsDeviceLog.error("Could not fetch bypassAppSheet");
	    		}
	    	}

	    	if (playStoreId != null && !bypassAppSheet) {
	    		openPlayStoreAsIntent(playStoreId);
	    	}
	    	else if (clickUrl != null ){
	    		openPlayStoreInBrowser(clickUrl);
	    	}
	    }
	}


	/* PRIVATE METHODS */

	private static void openPlayStoreAsIntent (String playStoreId) {
		UnityAdsDeviceLog.debug("Opening playstore activity with storeId: " + playStoreId);

		if (UnityAdsProperties.getCurrentActivity() != null) {
			try {
				UnityAdsProperties.getCurrentActivity().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + playStoreId)));
			}
			catch (Exception e) {
				UnityAdsDeviceLog.error("Couldn't start PlayStore intent!");
			}
		}
	}

	private static void openPlayStoreInBrowser (String url) {
		UnityAdsDeviceLog.debug("Opening playStore in browser: " + url);

		if (UnityAdsProperties.getCurrentActivity() != null) {
			try {
				UnityAdsProperties.getCurrentActivity().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
			}
			catch (Exception e) {
				UnityAdsDeviceLog.error("Couldn't start browser intent!");
			}
		}
	}

	public static void init (final Activity activity, String gameId, IUnityAdsListener listener) {
		if (_instance != null || _initialized) return;

		if(gameId.length() == 0) {
			throw new IllegalArgumentException("gameId is empty");
		} else {
			try {
				int gameIdInteger = Integer.parseInt(gameId);
				if(gameIdInteger <= 0) {
					throw new IllegalArgumentException("gameId is invalid");
				}
			} catch(NumberFormatException e) {
				throw new IllegalArgumentException("gameId does not parse as an integer");
			}
		}

		if (_instance == null) {
			_instance = new UnityAds();
		}

		setListener(listener);

		UnityAdsProperties.UNITY_ADS_GAME_ID = gameId;
		UnityAdsProperties.BASE_ACTIVITY = new WeakReference<Activity>(activity);
		UnityAdsProperties.CURRENT_ACTIVITY = new WeakReference<Activity>(activity);

		UnityAdsDeviceLog.debug("Is debuggable=" + UnityAdsUtils.isDebuggable(activity));

		cachemanager = new UnityAdsCacheManager();
		cachemanager.setDownloadListener(_instance);
		webdata = new UnityAdsWebData();
		webdata.setWebDataListener(_instance);

		//_instanceInitialized = true;

		new Thread(new Runnable() {
			@Override
			public void run() {
				UnityAdsAdvertisingId.init(activity);
				if (webdata.initCampaigns()) {
					_initialized = true;
				}
			}
		}).start();
	}

	private static void close () {
		cancelPauseScreenTimer();
		if(UnityAdsProperties.getCurrentActivity() != null && UnityAdsProperties.getCurrentActivity() instanceof UnityAdsFullscreenActivity) {
			UnityAdsCloseRunner closeRunner = new UnityAdsCloseRunner();
			Handler handler = new Handler(Looper.getMainLooper());
			handler.postDelayed(closeRunner, 1);
		}
		else {
			UnityAdsDeviceLog.debug("Did not close");
		}
	}

	private static void open (String view) {
		Boolean dataOk = true;
		JSONObject data = new JSONObject();

		try  {
			UnityAdsZone zone = UnityAdsWebData.getZoneManager().getCurrentZone();

			data.put(UnityAdsConstants.UNITY_ADS_WEBVIEW_API_ACTION_KEY, UnityAdsConstants.UNITY_ADS_WEBVIEW_API_OPEN);
			data.put(UnityAdsConstants.UNITY_ADS_WEBVIEW_API_ZONE_KEY, zone.getZoneId());

			if(zone.isIncentivized()) {
				UnityAdsRewardItemManager itemManager = ((UnityAdsIncentivizedZone)zone).itemManager();
				data.put(UnityAdsConstants.UNITY_ADS_WEBVIEW_API_REWARD_ITEM_KEY, itemManager.getCurrentItem().getKey());
			}
		}
		catch (Exception e) {
			dataOk = false;
		}

		UnityAdsDeviceLog.debug("DataOk: " + dataOk);

		if (dataOk && view != null) {
			UnityAdsDeviceLog.debug("Opening with view:" + view + " and data:" + data.toString());

			if (mainview != null) {
				mainview.openAds(view, data);

				UnityAdsZone currentZone = UnityAdsWebData.getZoneManager().getCurrentZone();
				if (currentZone.noOfferScreen()) {
					playVideo();
				}

				if (_adsListener != null)
					_adsListener.onShow();
			}
		}
	}

	private static void setup () {
		initCache();
		//setupViews();
	}

	private static void initCache () {
		UnityAdsDeviceLog.entered();
		if (_initialized) {
			// Update cache WILL START DOWNLOADS if needed, after this method you can check getDownloadingCampaigns which ones started downloading.
			cachemanager.updateCache(webdata.getVideoPlanCampaigns());
		}
	}

	private static void sendReadyEvent () {
		if (!_adsReadySent && _adsListener != null) {
			UnityAdsUtils.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					if(!_adsReadySent) {
						UnityAdsDeviceLog.debug("Unity Ads ready.");
						_adsListener.onFetchCompleted();
						_adsReadySent = true;
					}
				}
			});
		}
	}

	private static void setupViews () {
		if (mainview != null) {
			UnityAdsDeviceLog.debug("View was not destroyed, trying to destroy it");
			mainview.webview.destroy();
			mainview = null;
		}

		if (mainview == null) {
			mainview = new UnityAdsMainView(UnityAdsProperties.getCurrentActivity(), _instance, _instance);
		}
	}

	private static void playVideo () {
		playVideo(0);
	}

	private static void playVideo (long delay) {
		UnityAdsDeviceLog.debug("Running threaded");
		UnityAdsPlayVideoRunner playVideoRunner = new UnityAdsPlayVideoRunner();
		UnityAdsUtils.runOnUiThread(playVideoRunner, delay);
	}

	private static void startFullscreenActivity () {
		Intent newIntent = new Intent(UnityAdsProperties.getCurrentActivity(), com.unity3d.ads.android.view.UnityAdsFullscreenActivity.class);
		int flags = Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_NEW_TASK;

		UnityAdsZone currentZone = UnityAdsWebData.getZoneManager().getCurrentZone();
		if (currentZone.openAnimated()) {
			flags = Intent.FLAG_ACTIVITY_NEW_TASK;
		}

		newIntent.addFlags(flags);

		try {
			UnityAdsProperties.getBaseActivity().startActivity(newIntent);
		}
		catch (ActivityNotFoundException e) {
			UnityAdsDeviceLog.error("Could not find activity: " + e.getStackTrace());
		}
		catch (Exception e) {
			UnityAdsDeviceLog.error("Weird error: " + e.getStackTrace());
		}
	}

	private static void cancelPauseScreenTimer () {
		if (_pauseScreenTimer != null) {
			_pauseScreenTimer.cancel();
		}

		if (_pauseTimer != null) {
			_pauseTimer.cancel();
			_pauseTimer.purge();
		}

		_pauseScreenTimer = null;
		_pauseTimer = null;
	}

	private static void createPauseScreenTimer () {
		_pauseScreenTimer = new TimerTask() {
			@Override
			public void run() {
				if (UnityAdsProperties.getCurrentActivity() != null && UnityAdsProperties.getCurrentActivity().getBaseContext() != null) {
					PowerManager pm = (PowerManager)UnityAdsProperties.getCurrentActivity().getBaseContext().getSystemService(Context.POWER_SERVICE);
					if (!pm.isScreenOn()) {
						if (mainview != null && mainview.webview != null) {
							mainview.webview.sendNativeEventToWebApp(UnityAdsConstants.UNITY_ADS_NATIVEEVENT_HIDESPINNER, new JSONObject());
						}
						close();
						cancelPauseScreenTimer();
					}
				}
			}
		};

		_pauseTimer = new Timer();
		_pauseTimer.scheduleAtFixedRate(_pauseScreenTimer, 0, 50);
	}

	private static void refreshCampaigns() {
		if(_refreshAfterShowAds) {
			_refreshAfterShowAds = false;
			UnityAdsDeviceLog.debug("Starting delayed ad plan refresh");
			if(webdata != null) {
				webdata.initCampaigns();
			}
			return;
		}

		if(_campaignRefreshTimerDeadline > 0 && SystemClock.elapsedRealtime() > _campaignRefreshTimerDeadline) {
			removeCampaignRefreshTimer();
			UnityAdsDeviceLog.debug("Refreshing ad plan from server due to timer deadline");
			webdata.initCampaigns();
			return;
		}

		if (UnityAdsProperties.CAMPAIGN_REFRESH_VIEWS_MAX > 0) {
			if(UnityAdsProperties.CAMPAIGN_REFRESH_VIEWS_COUNT >= UnityAdsProperties.CAMPAIGN_REFRESH_VIEWS_MAX) {
				UnityAdsDeviceLog.debug("Refreshing ad plan from server due to endscreen limit");
				webdata.initCampaigns();
				return;
			}
		}

		if (webdata != null && webdata.getVideoPlanCampaigns() != null) {
			if(webdata.getViewableVideoPlanCampaigns().size() == 0) {
				UnityAdsDeviceLog.debug("All available videos watched, refreshing ad plan from server");
				webdata.initCampaigns();
				return;
			}
		} else {
			UnityAdsDeviceLog.error("Unable to read video data to determine if ad plans should be refreshed");
		}
	}

	private static void setupCampaignRefreshTimer() {
		removeCampaignRefreshTimer();

		if(UnityAdsProperties.CAMPAIGN_REFRESH_SECONDS > 0) {
			_campaignRefreshTimerTask = new TimerTask() {
				@Override
				public void run() {
					if(!_showingAds) {
						UnityAdsDeviceLog.debug("Refreshing ad plan to get new data");
						webdata.initCampaigns();
					} else {
						UnityAdsDeviceLog.debug("Refreshing ad plan after current ad");
						_refreshAfterShowAds = true;
					}
				}
			};

			_campaignRefreshTimerDeadline = SystemClock.elapsedRealtime() + UnityAdsProperties.CAMPAIGN_REFRESH_SECONDS * 1000;
			_campaignRefreshTimer = new Timer();
			_campaignRefreshTimer.schedule(_campaignRefreshTimerTask, UnityAdsProperties.CAMPAIGN_REFRESH_SECONDS * 1000);
		}
	}

	private static void removeCampaignRefreshTimer() {
		_campaignRefreshTimerDeadline = 0;

		if(_campaignRefreshTimer != null) {
			_campaignRefreshTimer.cancel();
		}
	}

	/* INTERNAL CLASSES */

	// FIX: Could these 2 classes be moved to MainView

	private static class UnityAdsCloseRunner implements Runnable {
		JSONObject _data = null;
		@Override
		public void run() {
			if (UnityAdsProperties.getCurrentActivity() != null && UnityAdsProperties.getCurrentActivity() instanceof UnityAdsFullscreenActivity) {
				Boolean dataOk = true;
				final JSONObject data = new JSONObject();

				try  {
					data.put(UnityAdsConstants.UNITY_ADS_WEBVIEW_API_ACTION_KEY, UnityAdsConstants.UNITY_ADS_WEBVIEW_API_CLOSE);
				}
				catch (Exception e) {
					dataOk = false;
				}

				UnityAdsDeviceLog.debug("DataOk: " + dataOk);

				if (dataOk) {
					final Activity currentActivity = UnityAdsProperties.getCurrentActivity();
					if (currentActivity != null && currentActivity instanceof UnityAdsFullscreenActivity && !currentActivity.isFinishing() && !UnityAdsProperties.isActivityDestroyed(currentActivity)) {
						currentActivity.finish();
						if (UnityAdsWebData.getZoneManager() != null) {
							UnityAdsZone currentZone = UnityAdsWebData.getZoneManager().getCurrentZone();
							if (!currentZone.openAnimated()) {
								currentActivity.overridePendingTransition(0, 0);
							}
						}
					}

					Handler handler = new Handler(Looper.getMainLooper());
					handler.postDelayed(new Runnable() {
						@Override
						public void run() {
							_data = data;

							if(mainview != null && mainview.webview != null) {
								mainview.webview.setWebViewCurrentView(UnityAdsConstants.UNITY_ADS_WEBVIEW_VIEWTYPE_NONE, data);
							}

							mainview.closeAds(_data);
							mainview = null;
							_showingAds = false;

							if (_adsListener != null)
								_adsListener.onHide();

							refreshCampaigns();
						}
					}, 30);
				}
			}
		}
	}

	private static class UnityAdsPlayVideoRunner implements Runnable {

		@Override
		public void run() {
			UnityAdsDeviceLog.entered();
			if (UnityAdsProperties.SELECTED_CAMPAIGN != null) {
				UnityAdsDeviceLog.debug("Selected campaign found");
				JSONObject data = new JSONObject();

				try {
					data.put(UnityAdsConstants.UNITY_ADS_TEXTKEY_KEY, UnityAdsConstants.UNITY_ADS_TEXTKEY_BUFFERING);
				}
				catch (Exception e) {
					UnityAdsDeviceLog.error("Couldn't create data JSON");
					return;
				}

				mainview.webview.sendNativeEventToWebApp(UnityAdsConstants.UNITY_ADS_NATIVEEVENT_SHOWSPINNER, data);

				createPauseScreenTimer();

				String playUrl = UnityAdsUtils.getCacheDirectory() + "/" + UnityAdsProperties.SELECTED_CAMPAIGN.getVideoFilename();
				if (!UnityAdsUtils.isFileInCache(UnityAdsProperties.SELECTED_CAMPAIGN.getVideoFilename()))
					playUrl = UnityAdsProperties.SELECTED_CAMPAIGN.getVideoStreamUrl();

				mainview.setViewState(UnityAdsMainViewState.VideoPlayer);
				UnityAdsDeviceLog.debug("Start videoplayback with: " + playUrl);
				mainview.videoplayerview.playVideo(playUrl);
			}
			else
				UnityAdsDeviceLog.error("Campaign is null");
		}
	}
}
