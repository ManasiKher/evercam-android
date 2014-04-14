package io.evercam.android;

import android.app.ActionBar;
import android.app.ActionBar.OnNavigationListener;
import android.bluetooth.BluetoothAdapter;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.view.Display;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.*;

import io.evercam.android.custom.AboutDialog;
import io.evercam.android.custom.CameraLayout;
import io.evercam.android.dal.DbAppUser;
import io.evercam.android.dal.DbCamera;
import io.evercam.android.dal.DbNotifcation;
import io.evercam.android.dto.AppUser;
import io.evercam.android.dto.Camera;
import io.evercam.android.dto.CameraNotification;
import io.evercam.android.dto.EvercamCamera;
import io.evercam.android.dto.ImageLoadingStatus;
import io.evercam.android.exceptions.ConnectivityException;
import io.evercam.android.exceptions.CredentialsException;
import io.evercam.android.slidemenu.*;
import io.evercam.android.tasks.LoadCameraListTask;
import io.evercam.android.utils.AppData;
import io.evercam.android.utils.Commons;
import io.evercam.android.utils.Constants;
import io.evercam.android.utils.UIUtils;

import java.util.ArrayList;
import java.util.List;

import com.bugsense.trace.BugSenseHandler;
import io.evercam.android.R;
import com.google.analytics.tracking.android.EasyTracker;
import com.google.android.gcm.GCMRegistrar;

import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

public class CamerasActivity extends ParentActivity implements
		SlideMenuInterface.OnSlideMenuItemClickListener
{
	public static CamerasActivity activity = null;

	private static final String TAG = "evecamapp";
	private RegisterGCMAlertsServiceTask RegisterTask = null;
	private MenuItem refresh;
	private SlideMenu slideMenu;
	private int totalCamerasInGrid = 0;
	private int slideoutMenuAnimationTime = 255;
	private boolean isUsersAccountsActivityStarted = false;
	private static int camerasPerRow = 2;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		if (Constants.isAppTrackingEnabled)
		{
			BugSenseHandler.initAndStartSession(this, Constants.bugsense_ApiKey);
		}

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		try
		{
			if (this.getActionBar() != null)
			{
				this.getActionBar().setHomeButtonEnabled(true);
				this.getActionBar().setDisplayShowTitleEnabled(false);
				this.getActionBar().setIcon(R.drawable.ic_device_access_storage);
			}

			setContentView(R.layout.camslayoutwithslide);

			addUsersToDropdownActionBar();

			slideMenu = (SlideMenu) findViewById(R.id.slideMenu);
			slideMenu.init(this, R.menu.slide, this, slideoutMenuAnimationTime);

			int notificationID = 0;
			try
			{
				activity = this;

				notificationID = this.getIntent().getIntExtra(Constants.GCMNotificationIDString, 0);
				this.getIntent().putExtra(Constants.GCMNotificationIDString, 0);
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
			if (notificationID > 0)
			{
				CamerasActivity.this.onSlideMenuItemClick(notificationID);
			}
			Log.i(TAG, "notificationID [" + notificationID + "]");

		}
		catch (Exception e)
		{
			Log.e(TAG, e.toString(), e);
			UIUtils.GetAlertDialog(
					CamerasActivity.this,
					"Error Occured",
					Constants.ErrorMessageGeneric + e.toString() + "::"
							+ Log.getStackTraceString(e)).show();
			if (Constants.isAppTrackingEnabled) BugSenseHandler.sendException(e);
		}

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		try
		{
			// draw the options defined in the following file
			MenuInflater inflater = getMenuInflater();
			inflater.inflate(R.menu.camsmenulayout, menu);

			refresh = menu.findItem(R.id.menurefresh);

			if (refresh != null && (AppData.cameraList == null || AppData.cameraList.size() == 0))
			{
				refresh.setActionView(null);
				refresh.setActionView(R.layout.actionbar_indeterminate_progress);
			}

			Log.i(TAG, "Options Activity Started in onPrepareOptionsMenu event");
			return true;
		}
		catch (Exception ex)
		{
			Log.e(TAG, ex.toString());
			if (Constants.isAppTrackingEnabled) BugSenseHandler.sendException(ex);
		}
		return true;
	}

	// Tells that the item has been selected from the menu. Now check and get
	// the selected item and perform the relevent action
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		try
		{
			switch (item.getItemId())
			{
//			case R.id.menurefresh: // need to refresh the application
//				if (refresh != null) refresh
//						.setActionView(R.layout.actionbar_indeterminate_progress);
//
//				GetUserCamsData task = new GetUserCamsData();
//				task.reload = true; // be default do not refesh until there is
//									// any change in cameras in database
//				task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, "");
//
//				return true;

			case android.R.id.home:
				slideMenu.show();

			default:
				return super.onOptionsItemSelected(item);
			}
		}
		catch (Exception e)
		{
			Log.e(TAG, e.toString(), e);
			UIUtils.GetAlertDialog(
					this,
					"Error Occured",
					"Some error occured while saving your options. Technical details are: "
							+ e.toString());
			if (Constants.isAppTrackingEnabled) BugSenseHandler.sendException(e);
		}
		return super.onOptionsItemSelected(item);

	}

	@Override
	public void onSlideMenuItemClick(int itemId)
	{
		try
		{
			switch (itemId)
			{
			case R.id.slidemenu_logout:

				// delete saved username and password
				SharedPreferences sharedPrefs = PreferenceManager
						.getDefaultSharedPreferences(CamerasActivity.this);
				SharedPreferences.Editor editor = sharedPrefs.edit();
				editor.putString("AppUserEmail", null);
				editor.putString("AppUserPassword", null);
				editor.commit();

				startActivity(new Intent(this, MainActivity.class));

				new LogoutTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, "");

				break;

			case R.id.slidemenu_about: // show the about dialog

				new Handler().postDelayed(new Runnable(){
					@Override
					public void run()
					{
						startActivity(new Intent(CamerasActivity.this, AboutDialog.class));
					}
				}, slideoutMenuAnimationTime);

				break;

			case R.id.slidemenu_settings:

				new Handler().postDelayed(new Runnable(){
					@Override
					public void run()
					{
						startActivity(new Intent(CamerasActivity.this, CamsPrefsActivity.class));
					}
				}, slideoutMenuAnimationTime);

				break;

			case R.id.slidemenu_manage: // show the manage dialog

				new Handler().postDelayed(new Runnable(){
					@Override
					public void run()
					{
						startActivity(new Intent(CamerasActivity.this, ManageAccountsActivity.class));
						isUsersAccountsActivityStarted = true;
					}
				}, slideoutMenuAnimationTime);
				break;

			default: // starting the notification activity

				NotificationActivity.NotificationID = itemId;
				new MarkNotificationAsReadTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
						itemId + "");
				new Handler().postDelayed(new Runnable(){
					@Override
					public void run()
					{
						Intent i = new Intent(new Intent(CamerasActivity.this,
								NotificationActivity.class));
						startActivity(i);
					}
				}, slideoutMenuAnimationTime);
				break;

			}
		}
		catch (Exception e)
		{
			Log.e(TAG, e.toString(), e);
			UIUtils.GetAlertDialog(
					this,
					"Error Occured",
					"Some error occured while saving your options. Technical details are: "
							+ e.toString());
		}
	}

	@Override
	public void onRestart()
	{
		super.onRestart();
		try
		{
			if (isUsersAccountsActivityStarted)
			{
				isUsersAccountsActivityStarted = false;
				addUsersToDropdownActionBar();
			}

			int camsOldValue = camerasPerRow;
			SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
			camerasPerRow = Integer.parseInt(sharedPrefs.getString("lstgridcamerasPerRow", "2"));

			if (camsOldValue != camerasPerRow)
			{
				removeAllCameraViews();
				addAllCameraViews(false); // do not reload cameras beacuse it
											// may be an activity for orentation
											// changed or notification might
											// have arrived.
			}

		}
		catch (Exception e)
		{
			if (Constants.isAppTrackingEnabled) BugSenseHandler.sendException(e);
		}
	}


	private void addUsersToDropdownActionBar()
	{
		boolean taskStarted = false;

		while (!taskStarted)
		{
			try
			{
				stopAllCameraViews();

				if (AppData.defaultUser == null)
				{
					startActivity(new Intent(this, MainActivity.class));
					CamerasActivity.this.finish();
					return;
				}

				new UserLoadingTask().execute();

				taskStarted = true;
			}
			catch (Exception e)
			{
				Log.e(TAG, e.getMessage(), e);
				if (Constants.isAppTrackingEnabled) BugSenseHandler.sendException(e);
			}
		}

	}
	
	// Stop All Camera Views
	private void stopAllCameraViews()
	{
		io.evercam.android.custom.FlowLayout camsLineView = (io.evercam.android.custom.FlowLayout) this
				.findViewById(R.id.camsLV);
		for (int count = 0; count < camsLineView.getChildCount(); count++)
		{
			LinearLayout linearLayout = (LinearLayout) camsLineView.getChildAt(count);
			CameraLayout cameraLayout = (CameraLayout) linearLayout.getChildAt(0);
			cameraLayout.stopAllActivity();
		}
	}

	boolean resizeCameras()
	{
		try
		{
			Display display = getWindowManager().getDefaultDisplay();
			int screen_width = display.getWidth();

			io.evercam.android.custom.FlowLayout camsLineView = (io.evercam.android.custom.FlowLayout) this
					.findViewById(R.id.camsLV);
			for (int i = 0; i < camsLineView.getChildCount(); i++)
			{
				LinearLayout pview = (LinearLayout) camsLineView.getChildAt(i);
				CameraLayout cl = (CameraLayout) pview.getChildAt(0); // CameraLayout
																		// is on
																		// 0th
																		// index

				LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
						android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
						android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
				params.width = ((i + 1 % camerasPerRow == 0) ? (screen_width - (i % camerasPerRow)
						* (screen_width / camerasPerRow)) : screen_width / camerasPerRow);
				params.height = (int) (params.width / (1.25));
				cl.setLayoutParams(params);
			}

			return true;
		}
		catch (Exception e)
		{
			Log.e(TAG, e.toString() + "::" + Log.getStackTraceString(e));
			UIUtils.GetAlertDialog(CamerasActivity.this, "Error Occured",
					Constants.ErrorMessageGeneric).show();
			if (Constants.isAppTrackingEnabled) BugSenseHandler.sendException(e);

		}
		return false;
	}

	// Remove all the cameras so that all activities being performed can be
	// stopped
	boolean removeAllCameraViews()
	{
		try
		{
			stopAllCameraViews();

			io.evercam.android.custom.FlowLayout camsLineView = (io.evercam.android.custom.FlowLayout) this
					.findViewById(R.id.camsLV);
			camsLineView.removeAllViews();

			totalCamerasInGrid = 0;

			return true;
		}
		catch (Exception e)
		{
			Log.e(TAG, e.toString() + "::" + Log.getStackTraceString(e));
			UIUtils.GetAlertDialog(CamerasActivity.this, "Error Occured",
					Constants.ErrorMessageGeneric).show();
			if (Constants.isAppTrackingEnabled) BugSenseHandler.sendException(e);

		}
		return false;
	}

	// Add all the cameras as per the rules
	public boolean addAllCameraViews(boolean reloadImages)
	{
		try
		{
			SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
			camerasPerRow = Integer.parseInt(sharedPrefs.getString("lstgridcamerasPerRow", ""
					+ camerasPerRow));

			io.evercam.android.custom.FlowLayout camsLineView = (io.evercam.android.custom.FlowLayout) this
					.findViewById(R.id.camsLV);

			Display display = getWindowManager().getDefaultDisplay();
			int screen_width = display.getWidth();

			int index = 0;
			totalCamerasInGrid = 0;
			
			for(EvercamCamera evercamCamera: AppData.evercamCameraList)
			{
				Log.v("evercamapp", "init camera"+ evercamCamera.getName() );
				LinearLayout cameraListLayout = new LinearLayout(this);
				
				int indexPlus = index + 1;

				if (reloadImages) evercamCamera.loadingStatus = ImageLoadingStatus.not_started;
				CameraLayout cameraLayout = new CameraLayout(this, evercamCamera);
				LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
						android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
						android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
				params.width = ((indexPlus % camerasPerRow == 0) ? (screen_width - (index % camerasPerRow)
						* (screen_width / camerasPerRow))
						: screen_width / camerasPerRow);
				params.height = (int) (params.width / (1.25));
				cameraLayout.setLayoutParams(params);

				cameraListLayout.addView(cameraLayout);
				camsLineView.addView(cameraListLayout,
						new io.evercam.android.custom.FlowLayout.LayoutParams(0, 0));
				index++;
				totalCamerasInGrid++;
			}
			
			if (this.getActionBar() != null) this.getActionBar().setHomeButtonEnabled(true);

			startgCMRegisterActions();

			if (refresh != null) refresh.setActionView(null);

			return true;
		}
		catch (Exception e)
		{
			Log.e(TAG, e.toString(), e);
			UIUtils.GetAlertDialog(CamerasActivity.this, "Error Occured",
					Constants.ErrorMessageGeneric).show();
			if (Constants.isAppTrackingEnabled) BugSenseHandler.sendException(e);

		}
		return false;
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		try
		{
			stopGcmRegisterActions();
			removeAllCameraViews();
		}
		catch (Exception e)
		{
			Log.e(TAG, e.toString(), e);
		}
	}

	boolean mHandleMessageReceiverRegistered = false;

	private final void startgCMRegisterActions()
	{
		try
		{
			RegisterTask = new RegisterGCMAlertsServiceTask();
			RegisterTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, "");

			registerReceiver(mHandleMessageReceiver, new IntentFilter("CambaGCMAlert"));

			mHandleMessageReceiverRegistered = true;

			Log.i(TAG,
					"registerReceiver(mHandleMessageReceiver,new IntentFilter(\"CambaGCMAlert\"));");
		}
		catch (Exception e)
		{
			if (Constants.isAppTrackingEnabled) BugSenseHandler.sendException(e);
		}

	}

	private final void stopGcmRegisterActions()
	{

		Log.i(TAG, "StopGcmRegisterActions called");

		if (mHandleMessageReceiverRegistered) // unregister only if registered
												// otherwise
												// illegalArgumentException
		unregisterReceiver(mHandleMessageReceiver);

	}

	private final BroadcastReceiver mHandleMessageReceiver = new BroadcastReceiver(){
		@Override
		public void onReceive(Context context, Intent intent)
		{

			Log.i(TAG, "AlertMessage Received ");

			String AlertMessage = intent.getStringExtra("AlertMessage");
			Log.i(TAG, "AlertMessage [" + AlertMessage + "]");

			String ApiCamera = intent.getStringExtra("ApiCamera");
			Log.i(TAG, "ApiCamera [" + ApiCamera + "]");

	//		Camera cam = CambaApiManager.ParseJsonObject(ApiCamera);

		}
	};

	@Override
	public void onConfigurationChanged(Configuration newConfig)
	{
		try
		{
			super.onConfigurationChanged(newConfig);

			resizeCameras();

		}
		catch (Exception e)
		{
			if (Constants.isAppTrackingEnabled) BugSenseHandler.sendException(e);
		}
	}

	@Override
	public void onStart()
	{
		super.onStart();

		if (Constants.isAppTrackingEnabled)
		{
			EasyTracker.getInstance().activityStart(this);
			if (Constants.isAppTrackingEnabled) BugSenseHandler.startSession(this);
		}
	}

	@Override
	public void onStop()
	{
		super.onStop();

		if (Constants.isAppTrackingEnabled)
		{
			EasyTracker.getInstance().activityStop(this);
			if (Constants.isAppTrackingEnabled) BugSenseHandler.closeSession(this);
		}
	}

	private class UserLoadingTask extends AsyncTask<String, String, String[]>
	{
		int defaultUserIndex = 0;

		@Override
		protected String[] doInBackground(String... arg0)
		{
			try
			{
				DbAppUser dbUser = new DbAppUser(CamerasActivity.this);
				AppData.appUsers = dbUser.getAllAppUsers(1000);

				// If it is the first time called when application
				// has been installed. Then user might be not in
				// database but in preferences. So we need to add it
				// to database as well.
//				AppUser old = dbUser.getAppUser(AppData.AppUserEmail);
//				if (old == null)
//				{
//					AppUser user = new AppUser();
//					user.setEmail(AppData.AppUserEmail + "");
//					user.setPassword(AppData.AppUserPassword + "");
//					user.setApiKey(AppData.cambaApiKey + "");
//					user.setIsDefault(true);
//					dbUser.addAppUser(user);
//				}

				final String[] userAccounts = new String[AppData.appUsers.size()];

				for (int i = 0; i < AppData.appUsers.size(); i++)
				{
					userAccounts[i] = AppData.appUsers.get(i).getEmail();
					if (AppData.appUsers.get(i).getIsDefault()) defaultUserIndex = i;
				}

				return userAccounts;

			}
			catch (Exception e)
			{
				Log.e(TAG, e.getMessage(), e);
				if (Constants.isAppTrackingEnabled) BugSenseHandler.sendException(e);
			}
			return null;
		}

		@Override
		protected void onPostExecute(String[] userAccounts)
		{
			try
			{
				ArrayAdapter<String> adapter = new ArrayAdapter<String>(CamerasActivity.this,
						android.R.layout.simple_spinner_dropdown_item, userAccounts);
				CamerasActivity.this.getActionBar().setNavigationMode(
						ActionBar.NAVIGATION_MODE_LIST);
				OnNavigationListener navigationListener = new OnNavigationListener(){
					@Override
					public boolean onNavigationItemSelected(int itemPosition, long itemId)
					{
						try
						{
							// set all current users default to false
							for (AppUser user : AppData.appUsers)
							{
								user.setIsDefault(false);
							}

							// set all db app users as false
							DbAppUser dbUser = new DbAppUser(CamerasActivity.this);
							dbUser.updateAllIsDefaultFalse();

							// set selected user's default to true
							AppUser user = AppData.appUsers.get(itemPosition);
							user.setIsDefault(true);
//							Commons.setDefaultUserForApp(CamerasActivity.this, user.getEmail(),
//									user.getPassword(), user.getApiKey(), true);
							dbUser.updateAppUser(user);

							Log.v("evercamapp", "use default user:" + user.getEmail());
							// load cameras for default user
							AppData.cameraList = new DbCamera(CamerasActivity.this)
									.getAllCamerasForEmailID(AppData.defaultUser.getEmail(), 500);
							removeAllCameraViews();
							addAllCameraViews(true);

							// start the task for default user to refresh camera list
							new LoadCameraListTask(AppData.defaultUser, CamerasActivity.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
							if (totalCamerasInGrid == 0 && refresh != null)
							{
								refresh.setActionView(null);
								refresh.setActionView(R.layout.actionbar_indeterminate_progress);
							}
						}
						catch (Exception e)
						{
							Log.e(TAG, e.getMessage(), e);
							if (Constants.isAppTrackingEnabled) 
							{
								BugSenseHandler.sendException(e);
							}
						}
						return false;
					}
				};

				getActionBar().setListNavigationCallbacks(adapter, navigationListener);
				getActionBar().setSelectedNavigationItem(defaultUserIndex);

			}
			catch (Exception e)
			{
				Log.e(TAG, e.getMessage(), e);
				if (Constants.isAppTrackingEnabled) BugSenseHandler.sendException(e);
			}
		}
	}

	// This class gets users cameras list from the api with proper login
//	private class GetUserCamsData extends AsyncTask<String, Void, String>
//	{
//		public boolean reload = false;
//
//		@Override
//		protected String doInBackground(String... login)
//		{
//			try
//			{
//				boolean updateDB = false;
//
//				SharedPreferences sharedPrefs = PreferenceManager
//						.getDefaultSharedPreferences(CamerasActivity.this);
////				AppData.AppUserEmail = sharedPrefs.getString("AppUserEmail", null);
////				AppData.AppUserPassword = sharedPrefs.getString("AppUserPassword", null);
//
//				ArrayList<Camera> cambaList = CambaApiManager.getCameraListAndSetKey();
//				for (Camera camera : cambaList)
//				{
//					camera.setUserEmail(AppData.AppUserEmail);
//					Log.v(TAG, camera.toString());
//				}
//
//				for (Camera cam : cambaList)
//				{
//					if (!AppData.camesList.contains(cam))
//					{
//						updateDB = true;
//						break;
//					}
//
//				}
//				if (!updateDB)
//				{
//					for (Camera cam1 : AppData.camesList)
//				{
//					if (!cambaList.contains(cam1))
//					{
//						updateDB = true;
//						break;
//					}
//				}
//				}
//				if (updateDB)
//				{
//					this.reload = true;
//					AppData.cameraList = cambaList;
//
//					DbCamera dbcam = new DbCamera(CamerasActivity.this);
//					dbcam.deleteCameraForEmail(AppData.defaultUser.getEmail());
//
//					for (Camera cam : AppData.cameraList)
//					{
//						Log.i(TAG, cam.toString());
//						dbcam.addCamera(cam);
//					}
//				}
//			}
//
//			catch (CredentialsException ce)
//			{
//				if (Constants.isAppTrackingEnabled) BugSenseHandler.sendException(ce);
//				return Constants.ErrorMessageInvalidCredentialsAndLogout;
//
//			}
//			catch (ConnectivityException e)
//			{
//				if (Constants.isAppTrackingEnabled) BugSenseHandler.sendException(e);
//				return Constants.ErrorMessageNoConnectivity;
//			}
//			catch (Exception e)
//			{
//				if (Constants.isAppTrackingEnabled) BugSenseHandler.sendException(e);
//				if (AppData.camesList == null && AppData.camesList.size() == 0) return Constants.ErrorMessageRefreshCamerasWhenNoCamerasExist;
//				else return Constants.ErrorMessageRefreshCamerasWhenCamerasLoadedFromLocalDB;
//			}
//			return null;
//		}
//
//		@Override
//		protected void onPostExecute(final String result)
//		{
//			if (result == null)
//			{
//				if (this.reload || totalCamerasInGrid != AppData.camesList.size())
//				{
//					CamerasActivity.this.removeAllCameraViews();
//					CamerasActivity.this.addAllCameraViews(true);
//				}
//			}
//			else
//			{
//				if (!CamerasActivity.this.isFinishing()) UIUtils.GetAlertDialog(
//						CamerasActivity.this, "Error Occured", result,
//						new DialogInterface.OnClickListener(){
//							@Override
//							public void onClick(DialogInterface dialog, int which)
//							{
//								dialog.dismiss();
//								// CamerasActivity.this.finish(); // cannot
//								// finish
//								// because if we finish, user will not be able
//								// to login back again.
//								if ((result + "")
//										.equalsIgnoreCase(Constants.ErrorMessageInvalidCredentialsAndLogout))
//								{
//									// delete saved username and apssword
//									SharedPreferences sharedPrefs = PreferenceManager
//											.getDefaultSharedPreferences(CamerasActivity.this);
//									SharedPreferences.Editor editor = sharedPrefs.edit();
//									editor.putString("AppUserEmail", null);
//									editor.putString("AppUserPassword", null);
//									editor.commit();
//
//									startActivity(new Intent(CamerasActivity.this,
//											MainActivity.class));
//									new LogoutTask().executeOnExecutor(
//											AsyncTask.THREAD_POOL_EXECUTOR, "");
//								}
//								if (CamerasActivity.this.refresh != null) CamerasActivity.this.refresh
//										.setActionView(null);
//							}
//						}).show();
//			}
//		}
//	}

	private class LogoutTask extends AsyncTask<String, String, String>
	{
		@Override
		protected String doInBackground(String... params)
		{
			try
			{
				// delete saved username and apssword
				SharedPreferences sharedPrefs = PreferenceManager
						.getDefaultSharedPreferences(CamerasActivity.this);
				SharedPreferences.Editor editor = sharedPrefs.edit();
				editor.putString("AppUserEmail", null);
				editor.putString("AppUserPassword", null);
				editor.commit();

				// Un register from gcm server
				GCMRegistrar.setRegisteredOnServer(CamerasActivity.this, false);

				// delete all app users
				DbAppUser dbu = new DbAppUser(CamerasActivity.this);
				List<AppUser> list = dbu.getAllAppUsers(10000);
				if (list != null && list.size() > 0)
				{
					for (AppUser user : list)
					{
						dbu.deleteAppUserByEmail(user.getEmail());
					}
				}

				// unregister all users
				if (list != null && list.size() > 0)
				{
					// get information to be posted for unregister on camba
					// server request
					String regId = GCMRegistrar.getRegistrationId(CamerasActivity.this);
					String AppUserEmail = null;
					String AppUserPassword = null;
					String Operation = null;
					String Manufacturer = null;
					String Model = null;
					String SerialNo = null;
					String ImeiNo = null;
					String Fingureprint = null;
					String MacAddress = null;
					String BlueToothName = null;
					String AppVersion = null;

					try
					{

						AppUserPassword = sharedPrefs.getString("AppUserPassword", null);
						Operation = "2";
						Manufacturer = android.os.Build.MANUFACTURER;
						Model = android.os.Build.MODEL;
						SerialNo = android.os.Build.SERIAL;
						ImeiNo = ((android.telephony.TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE))
								.getDeviceId();
						Fingureprint = android.os.Build.FINGERPRINT;
						WifiManager manager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
						WifiInfo info = manager.getConnectionInfo();
						MacAddress = info.getMacAddress();
						BlueToothName = BluetoothAdapter.getDefaultAdapter().getName();
						AppVersion = (getPackageManager().getPackageInfo(getPackageName(), 0)).versionName;
					}
					catch (Exception ee)
					{
					}

					for (AppUser user : list)
					{
						dbu.deleteAppUserByEmail(user.getEmail());
						AppUserEmail = user.getEmail();
						try
						{
//							CambaApiManager.registerDeviceForUsername(AppUserEmail,
//									AppUserPassword, regId, Operation, BlueToothName, Manufacturer,
//									Model, SerialNo, ImeiNo, Fingureprint, MacAddress, AppVersion);
						}
						catch (Exception e)
						{
						}
					}
				}
			}
			catch (Exception eee)
			{
			}
			return null;
		}

		@Override
		protected void onPostExecute(String result)
		{
			CamerasActivity.this.finish();

		}
	}

	private class MarkNotificationAsReadTask extends AsyncTask<String, String, String>
	{
		@Override
		protected String doInBackground(String... id)
		{
			String message = "";
			try
			{
				DbNotifcation helper = new DbNotifcation(CamerasActivity.this);

				CameraNotification notif = helper.getCameraNotification(Integer.parseInt(id[0]));
				notif.setIsRead(true);
				helper.updateCameraNotification(notif);

			}
			catch (Exception e)
			{
				Log.e(TAG, e.toString() + "::" + Log.getStackTraceString(e));
				message = e.toString();
			}
			return message;
		}
	}

	private class RegisterGCMAlertsServiceTask extends AsyncTask<String, Void, String>
	{
		@Override
		protected String doInBackground(String... usernames)
		{
			String message = "";
			String TAG = "RegisterTask";
			try
			{
				GCMRegistrar.checkDevice(CamerasActivity.this);
				Log.i(TAG, "Device Checked");
				GCMRegistrar.checkManifest(CamerasActivity.this);
				Log.i(TAG, "Manifest Checked");
				String regId = GCMRegistrar.getRegistrationId(CamerasActivity.this); // registration
																						// id
																						// for
																						// this
				String AppUserEmail = null;
				String AppUserPassword = null;
				String Operation = null;
				String Manufacturer = null;
				String Model = null;
				String SerialNo = null;
				String ImeiNo = null;
				String Fingureprint = null;
				String MacAddress = null;
				String BlueToothName = null;
				String AppVersion = null;

				try
				{

					SharedPreferences sharedPrefs = PreferenceManager
							.getDefaultSharedPreferences(CamerasActivity.this);
					AppUserEmail = sharedPrefs.getString("AppUserEmail", null);
					AppUserPassword = sharedPrefs.getString("AppUserPassword", null);
					Operation = "1";
					Manufacturer = android.os.Build.MANUFACTURER;
					Model = android.os.Build.MODEL;
					SerialNo = android.os.Build.SERIAL;
					ImeiNo = ((android.telephony.TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE))
							.getDeviceId();
					Fingureprint = android.os.Build.FINGERPRINT;
					WifiManager manager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
					WifiInfo info = manager.getConnectionInfo();
					MacAddress = info.getMacAddress();
					BlueToothName = BluetoothAdapter.getDefaultAdapter().getName();
					AppVersion = (getPackageManager().getPackageInfo(getPackageName(), 0)).versionName;
				}
				catch (Exception ee)
				{
				}

				Log.i(TAG, "regId [" + regId + "] ");
				// *
				if (regId.equals(""))
				{ // New Registration on GCM Server
					// Automatically registers application on startup.
					GCMRegistrar.register(CamerasActivity.this, Constants.GCM_SENDER_ID);
					return "Device registered successfully on GCM Server. It will be registered on camba server shortly.";

				}
				else if (!GCMRegistrar.isRegisteredOnServer(CamerasActivity.this))
				{
//					if (CambaApiManager.registerDeviceForUsername(AppUserEmail, AppUserPassword,
//							regId, Operation, BlueToothName, Manufacturer, Model, SerialNo, ImeiNo,
//							Fingureprint, MacAddress, AppVersion))
//					{
//						return "Device successfully registerd on GCM Server and Camba Server.";
//					}
//					else
//					{
//						return "Device failed to register on Camba server.";
//					}
				}
				else
				{
//					CambaApiManager.registerDeviceForUsername(AppUserEmail, AppUserPassword, regId,
//							Operation, BlueToothName, Manufacturer, Model, SerialNo, ImeiNo,
//							Fingureprint, MacAddress, AppVersion);
					GCMRegistrar.setRegisteredOnServer(CamerasActivity.this, true);

					// return
					// "Device is already registered on GCM Server with ID ["+regId+"] but was unable to register on camba server.";
					return regId;
				}
			}
			catch (Exception e)
			{
				Log.e(TAG, e.toString() + "::" + Log.getStackTraceString(e));
				message = e.toString();
				if (Constants.isAppTrackingEnabled) BugSenseHandler.sendException(e);
			}
			catch (Error e)
			{
				if (Constants.isAppTrackingEnabled) BugSenseHandler.sendExceptionMessage(TAG,
						"Error", new Exception(Log.getStackTraceString(e)));
			}
			return message;
		}
	}
}
