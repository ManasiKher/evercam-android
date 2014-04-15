package io.evercam.android.dto;

import io.evercam.EvercamException;

import java.util.ArrayList;

import org.apache.http.cookie.Cookie;

import android.util.Log;

public class EvercamCamera
{
	public ImageLoadingStatus loadingStatus = ImageLoadingStatus.not_started;
	public ArrayList<Cookie> cookies = null;
	
	private final String TAG = "evercamapp-EvercamCamera";
	private String cameraId;
	private String externalSnapshotUrl;
	private String internalSnapshotUrl;
	private String name;
	private String status;
	private String username;
	private String password;
	private String timezone;
	private String model;
	private String vendor;
	private String owner;
	
	public EvercamCamera()
	{
		
	}
	
	public EvercamCamera convertFromEvercam(io.evercam.Camera camera)
	{
		try
		{
			cameraId = camera.getId();
			name = camera.getName();
			username = camera.getCameraUsername();
			password = camera.getCameraPassword();
			externalSnapshotUrl = camera.getJpgExternalFullUrl();		
			internalSnapshotUrl = camera.getJpgInternalFullUrl();
			timezone = camera.getTimezone();
			model = camera.getModel();
			vendor = camera.getVendor();
			owner = camera.getOwner();
		}
		catch (EvercamException e)
		{
			Log.e(TAG,e.getMessage());
		}
		return this;
	}
	
	public String getStatus()
	{
		return "Active";
	}

	public String getCameraId()
	{
		return cameraId;
	}

	public String getExternalSnapshotUrl()
	{
		return externalSnapshotUrl;
	}

	public String getInternalSnapshotUrl()
	{
		return internalSnapshotUrl;
	}

	public String getName()
	{
		return name;
	}

	public String getUsername()
	{
		return username;
	}

	public String getPassword()
	{
		return password;
	}

	public String getTimezone()
	{
		return timezone;
	}

	public String getModel()
	{
		return model;
	}

	public String getVendor()
	{
		return vendor;
	}

	public String getOwner()
	{
		return owner;
	}

	public void setCameraId(String cameraId)
	{
		this.cameraId = cameraId;
	}

	public void setExternalSnapshotUrl(String externalSnapshotUrl)
	{
		this.externalSnapshotUrl = externalSnapshotUrl;
	}

	public void setInternalSnapshotUrl(String internalSnapshotUrl)
	{
		this.internalSnapshotUrl = internalSnapshotUrl;
	}

	public void setName(String name)
	{
		this.name = name;
	}

	public void setUsername(String username)
	{
		this.username = username;
	}

	public void setPassword(String password)
	{
		this.password = password;
	}

	public void setTimezone(String timezone)
	{
		this.timezone = timezone;
	}

	public void setModel(String model)
	{
		this.model = model;
	}

	public void setVendor(String vendor)
	{
		this.vendor = vendor;
	}

	public void setOwner(String owner)
	{
		this.owner = owner;
	}
}