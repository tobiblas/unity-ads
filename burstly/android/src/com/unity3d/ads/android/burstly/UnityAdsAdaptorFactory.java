package com.unity3d.ads.android.burstly;

import java.util.Map;

import android.content.Context;
import android.util.Log;

import com.unity3d.ads.android.UnityAds;
import com.unity3d.ads.android.burstly.UnityAdsAdaptor;

import com.burstly.lib.component.IBurstlyAdaptor;
import com.burstly.lib.feature.networks.IAdaptorFactory;

/**
 * Adaptor for Unity Ads
 * 
 * @author tuomasrinta
 *
 */
public class UnityAdsAdaptorFactory implements IAdaptorFactory {
	
	static {
		Log.d(UnityAdsAdaptor.UNITY_ADS_BURSTLY_LOG_TAG, "class load");
	}

    /**
     * A key for context object being passed in parameters.
     */
    private static final String CONTEXT = "context";
 
    /**
     * A key for current BurstlyView id object being passed in parameters.
     */
    private static final String VIEW_ID = "viewId";

    /**
     * A key for adaptor name being passed in parameters.
     */
    private static final String ADAPTOR_NAME = "adaptorName";
    

	@Override
	public IBurstlyAdaptor createAdaptor(Map<String, ?> params) {
		
		Log.d("burstly_unityads", "Creating adaptor w/ Context:" + params.get(UnityAdsAdaptorFactory.CONTEXT).getClass().getName());
		
		return new UnityAdsAdaptor(
				(Context)params.get(UnityAdsAdaptorFactory.CONTEXT)
			);
		
	}

	@Override
	public void destroy() {}

	@Override
	public void initialize(Map<String, ?> arg0) throws IllegalArgumentException {
		// Since we don't have the Game ID here, we can't init the adaptor yet
	}

	@Override
	public String getAdaptorVersion() {
		return UnityAdsAdaptor.UNITY_ADS_ADAPTOR_VERSION;
	}

	@Override
	public String getSdkVersion() {
		return UnityAds.getSDKVersion();
	}
}
