package org.greenstand.android.TreeTracker.activities;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;

import org.greenstand.android.TreeTracker.BuildConfig;
import org.greenstand.android.TreeTracker.R;
import org.greenstand.android.TreeTracker.camera.AlbumStorageDirFactory;
import org.greenstand.android.TreeTracker.camera.BaseAlbumDirFactory;
import org.greenstand.android.TreeTracker.camera.CameraPreview;
import org.greenstand.android.TreeTracker.camera.FroyoAlbumDirFactory;
import org.greenstand.android.TreeTracker.utilities.Utils;
import org.greenstand.android.TreeTracker.utilities.ValueHelper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import timber.log.Timber;


public class CameraActivity extends Activity implements PictureCallback, OnClickListener, ActivityCompat.OnRequestPermissionsResultCallback {

    private Camera mCamera;
    private CameraPreview mPreview;
	private String TAG = "Camera activity";
	private PictureCallback mPicture;
	private AlbumStorageDirFactory mAlbumStorageDirFactory = null;
	private String mCurrentPhotoPath;
	private ImageView mImageView;
	private View mParameters;
	private byte[] mCurrentPictureData;
	private ImageButton cancelImg;
	private ImageButton captureButton;
	private File tmpImageFile;
	private AsyncTask<String, Void, String> openCameraTask;
	private boolean safeToTakePicture = true;
    
    public static final int MEDIA_TYPE_IMAGE = 1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.camera_preview);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
			mAlbumStorageDirFactory = new FroyoAlbumDirFactory();
		} else {
			mAlbumStorageDirFactory = new BaseAlbumDirFactory();
		}

        mImageView = (ImageView) findViewById(R.id.camera_preview_taken);
        

        cancelImg = (ImageButton) findViewById(R.id.camera_preview_cancel);
        captureButton = (ImageButton) findViewById(R.id.button_capture);

        

     // Add a listener to the buttons
        captureButton.setOnClickListener(CameraActivity.this);
        cancelImg.setOnClickListener(CameraActivity.this);

        
        openCameraTask = new OpenCameraTask().execute(new String[]{});
        
    }
    
    /** A safe way to get an instance of the Camera object. */
    public static Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
        }
        catch (Exception e){
            Log.i("in use", e.getLocalizedMessage());
        }
        return c; // returns null if camera is unavailable
    }
    
    /** Check if this device has a camera */
    private boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
            // this device has a camera
            return true;
        } else {
            // no camera on this device
            return false;
        }
    }

	public void onPictureTaken(byte[] data, Camera camera) {
		captureButton.setVisibility(View.INVISIBLE);
		cancelImg.setVisibility(View.INVISIBLE);
		
		mCurrentPictureData = data;
		tmpImageFile = null;
		try {
			tmpImageFile = File.createTempFile("tmpimage.jpg", null, getCacheDir());
		}
		catch (IOException e) {
			Log.e("file not", "created");
			e.printStackTrace();
		}

		try {
			FileOutputStream fo = new FileOutputStream(tmpImageFile);
            fo.write(data);
            fo.close();
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

        checkBlurriness();
/*
        setPic();
		safeToTakePicture = true;
		savePicture();      //skip picture preview
		releaseCamera();
		*/
	}

	private void checkBlurriness(){
        Bitmap bitmap = openImage();
        if(bitmap == null){
            return;
        }

        // Calculate Sharpness - experimental
        // https://stackoverflow.com/questions/21107560/simple-way-to-check-if-an-image-bitmap-is-blur
        double n1sumR = 0;
        double n2sumR = 0;
        double n1sumG = 0;
        double n2sumG = 0;
        double n1sumB = 0;
        double n2sumB = 0;
        for(int y=1;y<bitmap.getHeight();y++) {
            for (int x = 1; x < bitmap.getWidth(); x++) {
                int pixel0 = bitmap.getPixel(x,y);
                int pixel1 = bitmap.getPixel(x-1,y);
                int pixel2 = bitmap.getPixel(x,y-1);

                int A0 = (pixel0 >> 24) & 0xff; // or color >>> 24
                int R0 = (pixel0 >> 16) & 0xff;
                int G0 = (pixel0 >>  8) & 0xff;
                int B0 = (pixel0      ) & 0xff;

                int A1 = (pixel1 >> 24) & 0xff; // or color >>> 24
                int R1 = (pixel1 >> 16) & 0xff;
                int G1 = (pixel1 >>  8) & 0xff;
                int B1 = (pixel1      ) & 0xff;

                int A2 = (pixel2 >> 24) & 0xff; // or color >>> 24
                int R2 = (pixel2 >> 16) & 0xff;
                int G2 = (pixel2 >>  8) & 0xff;
                int B2 = (pixel2      ) & 0xff;


                n1sumR= n1sumR + Math.abs(R0-A1) + Math.abs(R0-R2);
                n2sumR = n2sumR + (double) ( (R0-R1)^2 + (R0-R2)^2 );

                n1sumG= n1sumG + Math.abs(G0-G1) + Math.abs(G0-G2);
                n2sumG = n2sumG + (double) ( (G0-G1)^2 + (G0-G2)^2 );

                n1sumB= n1sumB + Math.abs(B0-B1) + Math.abs(B0-B2);
                n2sumB = n2sumB + (double)  ( (B0-B1)^2 + (B0-B2)^2 );

            }
        }

        double N_pixel = bitmap.getHeight() * bitmap.getWidth();
        double n1R = n1sumR / (2 * N_pixel);
        double n2R = n2sumR / (2 * N_pixel);
        double blurinessR = n2R / (n1R*n1R);
        double n1G = n1sumG / (2 * N_pixel);
        double n2G = n2sumG / (2 * N_pixel);
        double blurinessG = n2G / (n1G*n1G);
        double n1B = n1sumB / (2 * N_pixel);
        double n2B = n2sumB / (2 * N_pixel);
        double blurinessB = n2B / (n1B*n1B);

        double blurinessMean = (blurinessR + blurinessG + blurinessB) /3 ;

        AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(this, R.style.myDialog));
        builder.setTitle("Blurriness Calculated")
                .setMessage(String.valueOf(blurinessMean))
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        setPic();
                        safeToTakePicture = true;
                        savePicture();      //skip picture preview
                        releaseCamera();                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }
	
	private void galleryAddPic() throws IOException {
		Intent mediaScanIntent = new Intent(
				"android.intent.action.MEDIA_SCANNER_SCAN_FILE");
		
		Bitmap photo = Utils.resizedImage(mCurrentPhotoPath);
		
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		photo.compress(Bitmap.CompressFormat.JPEG, 70, bytes);

		File f = new File(mCurrentPhotoPath);
		try {
			f.createNewFile();
			FileOutputStream fo = new FileOutputStream(f);
			fo.write(bytes.toByteArray());
			fo.close();
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		
//		File f = new File(mCurrentPhotoPath);
		Uri contentUri = FileProvider.getUriForFile(CameraActivity.this,
				BuildConfig.APPLICATION_ID + ".provider", createImageFile());
		//Uri contentUri = Uri.fromFile(f);
		mediaScanIntent.setData(contentUri);
		sendBroadcast(mediaScanIntent);
	}
	
	/** Create a file Uri for saving an image or video */
	private static Uri getOutputMediaFileUri(int type){
	      return Uri.fromFile(getOutputMediaFile(type));
	}

	/** Create a File for saving an image or video */
	private static File getOutputMediaFile(int type){
	    // To be safe, you should check that the SDCard is mounted
	    // using Environment.getExternalStorageState() before doing this.

	    File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
	              Environment.DIRECTORY_PICTURES), "MyCameraApp");
	    // This location works best if you want the created images to be shared
	    // between applications and persist after your app has been uninstalled.

	    // Create the storage directory if it does not exist
	    if (! mediaStorageDir.exists()){
	        if (! mediaStorageDir.mkdirs()){
	            Log.d("MyCameraApp", "failed to create directory");
	            return null;
	       	}
	    }

	    // Create a media file name
	    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
	    File mediaFile;
	    if (type == MEDIA_TYPE_IMAGE){
	        mediaFile = new File(mediaStorageDir.getPath() + File.separator +
	        "IMG_"+ timeStamp + ".jpg");
	    } else {
	        return null;
	    }
	    return mediaFile;
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		releaseCamera();       // release the camera immediately on pause event
	}

	private void releaseCamera(){
		if (mCamera != null){
		    mCamera.release();        // release the camera for other applications
		    mCamera = null;
			Timber.d("camera released");
		}
	}
	
	private String getAlbumName() {
		return getString(R.string.album_name);
	}
	
	private File getAlbumDir() {
		File storageDir = null;

		if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
			storageDir = mAlbumStorageDirFactory.getAlbumStorageDir(getAlbumName());
			if (storageDir != null) {
				if (!storageDir.mkdirs()) {
					if (!storageDir.exists()) {
						Log.d("CameraSample", "failed to create directory");
						return null;
					}
				}
			}
		} else {
			Log.v(getString(R.string.app_name),"External storage is not mounted READ/WRITE.");
		}
		return storageDir;
	}
	
	private File createImageFile() throws IOException {
		// Create an image file name
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		String imageFileName = ValueHelper.JPEG_FILE_PREFIX + timeStamp + "_";
		File albumF = getAlbumDir();
		File imageF = File.createTempFile(imageFileName,ValueHelper.JPEG_FILE_SUFFIX, albumF);

		return imageF;
	}
	
	private File setUpPhotoFile() throws IOException {
		File f = createImageFile();
		mCurrentPhotoPath = f.getAbsolutePath();

		return f;
	}

	private Bitmap openImage(){
	    /* There isn't enough memory to open up more than a couple camera photos */
		/* So pre-scale the target bitmap into which the file is decoded */

		/* Get the size of the image */
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(tmpImageFile.getAbsolutePath(), bmOptions);
        int imageWidth = bmOptions.outWidth;

        // Calculate your sampleSize based on the requiredWidth and
        // originalWidth
        // For e.g you want the width to stay consistent at 500dp
        int requiredWidth = (int) (500 * getResources().getDisplayMetrics().density);

        int sampleSize = (int) Math.ceil((float) imageWidth / (float) requiredWidth);

        Log.e("sampleSize ", Integer.toString(sampleSize));
        // If the original image is smaller than required, don't sample
        if (sampleSize < 1) {
            sampleSize = 1;
        }

        Log.e("sampleSize 2 ", Integer.toString(sampleSize));
        bmOptions.inSampleSize = sampleSize;
        bmOptions.inPurgeable = true;
        bmOptions.inPreferredConfig = Bitmap.Config.RGB_565;
        bmOptions.inJustDecodeBounds = false;

		/* Decode the JPEG file into a Bitmap */
        Bitmap bitmap = BitmapFactory.decodeFile(tmpImageFile.getAbsolutePath(), bmOptions);
        return bitmap;
    }

	private void setPic() {

        Bitmap bitmap = openImage();
		if (bitmap == null) {
			return;
		}


		ExifInterface exif = null;
		try {
			exif = new ExifInterface(tmpImageFile.getAbsolutePath());
		} 
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		String orientString = exif.getAttribute(ExifInterface.TAG_ORIENTATION);
		int orientation = orientString != null ? Integer.parseInt(orientString)
				: ExifInterface.ORIENTATION_NORMAL;
		int rotationAngle = 0;
		if (orientation == ExifInterface.ORIENTATION_ROTATE_90)
			rotationAngle = 90;
		if (orientation == ExifInterface.ORIENTATION_ROTATE_180)
			rotationAngle = 180;
		if (orientation == ExifInterface.ORIENTATION_ROTATE_270)
			rotationAngle = 270;

		Matrix matrix = new Matrix();
		matrix.setRotate(rotationAngle, (float) bitmap.getWidth() / 2,
				(float) bitmap.getHeight() / 2);
		Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0,
				bitmap.getWidth(), bitmap.getHeight(), matrix, true);

		/* Associate the Bitmap to the ImageView */
		mImageView.setImageBitmap(rotatedBitmap);
		mImageView.setVisibility(View.VISIBLE);
	}
	
	public void onOrientationChanged(int orientation) {
	  
	}
	
	public void onClick(View v) {
		v.setHapticFeedbackEnabled(true);
        // get an image from the camera
        if(safeToTakePicture) {
            safeToTakePicture = false;
            mCamera.takePicture(null, null, CameraActivity.this);
            Log.e("take", "pic");
		}
	}

	private void savePicture(){
        File pictureFile = null;
        try {
            pictureFile = setUpPhotoFile();
            mCurrentPhotoPath = pictureFile.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
            pictureFile = null;
            mCurrentPhotoPath = null;
        }
        boolean saved = true;
        try {
        	FileOutputStream fos = new FileOutputStream(pictureFile);
            fos.write(mCurrentPictureData);
            fos.close();
            galleryAddPic();
        } catch (FileNotFoundException e) {
            Log.d(TAG, "File not found: " + e.getMessage());
            saved = false;
        } catch (IOException e) {
            Log.d(TAG, "Error accessing file: " + e.getMessage());
            saved = false;
        } catch (Exception e) {
            Log.d(TAG, "Error accessing file: " + e.getMessage());
            saved = false;
        }

        if (saved) {
            Intent data = new Intent();
            data.putExtra(ValueHelper.TAKEN_IMAGE_PATH, mCurrentPhotoPath);
            setResult(Activity.RESULT_OK, data);

        } else {
            setResult(Activity.RESULT_CANCELED);
        }
        finish();
    }

    
	class OpenCameraTask extends AsyncTask<String, Void, String> {
    	
		protected void onPreExecute() {
			cancelImg.setVisibility(View.INVISIBLE);
			captureButton.setVisibility(View.INVISIBLE);
		}

		@Override
		protected String doInBackground(String... params) {
			mCamera = getCameraInstance();
			return null;
		}	

		protected void onPostExecute(String response) {
			super.onPostExecute(response);

			if (mCamera == null) {
				openCameraTask = new OpenCameraTask().execute(new String[]{});
			} else {
				// Create our Preview view and set it as the content of our activity.
				mPreview = new CameraPreview(CameraActivity.this, mCamera);
				FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
				preview.addView(mPreview);
				cancelImg.setVisibility(View.VISIBLE);
				captureButton.setVisibility(View.VISIBLE);
			}
		}
	}
}



