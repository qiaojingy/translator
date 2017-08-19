package edu.stanford.ee368.translator;


import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;


import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.services.translate.Translate;
import com.google.api.services.translate.model.TranslationsListResponse;
import com.google.api.services.translate.model.TranslationsResource;
import com.googlecode.leptonica.android.WriteFile;
import com.googlecode.tesseract.android.ResultIterator;
import com.googlecode.tesseract.android.TessBaseAPI;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import static com.googlecode.tesseract.android.TessBaseAPI.PageIteratorLevel.RIL_WORD;

public class Translator extends Activity
{   private TessBaseAPI tessBaseAPI;

	protected Button _button;
	protected ImageView _image;
	protected TextView _field;
	protected String _path;
	protected boolean _taken;
	
	protected static final String PHOTO_TAKEN	= "photo_taken";

	private static final int SELECT_PICTURE = 1;

	private MenuItem               mStep0;
	private MenuItem               mStep1;
	private MenuItem               mStep2;
	private MenuItem               mStep3;
	private MenuItem               mStep4;
	private MenuItem               mStep5;
	private MenuItem               mStep6;
	private MenuItem               mStep7;
	private MenuItem               mStep8;
	private MenuItem               mStep9;
	private MenuItem               mStep10;
	private MenuItem               mStep11;
	private MenuItem               mStep12;

	protected Mat mRgba_original;
	protected Mat mRgba_rotation;
	protected Mat mRgba_binarize;
	protected Mat mRgba_contour;
	protected Mat mRgba_detected;
	protected Mat mRgba_erased;
	protected Mat mRgba_trans;
	protected Mat mRgba_hough;
	protected Mat mRgba_thresh;
	protected Mat mRgba_blurring;
	protected Mat mRgba_blurringh;
	protected Mat mRgba_blurringv;
	protected Mat mRgba_noiserm;

	private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
		@Override
		public void onManagerConnected(int status) {
			switch (status) {
				case LoaderCallbackInterface.SUCCESS:
				{
				} break;
				default:
				{
					super.onManagerConnected(status);
				} break;
			}
		}
	};

    @Override
    public void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.main);
       
        _image = ( ImageView ) findViewById( R.id.image );
        _field = ( TextView ) findViewById( R.id.field );
        _button = ( Button ) findViewById( R.id.button );
        _button.setOnClickListener( new ButtonClickHandler() );
		findViewById(R.id.button_select)
				.setOnClickListener(new View.OnClickListener() {

					public void onClick(View arg0) {

						// in onCreate or any event where your want the user to
						// select a file
						Intent intent = new Intent();
						intent.setType("image/*");
						intent.setAction(Intent.ACTION_GET_CONTENT);
						startActivityForResult(Intent.createChooser(intent,
								"Select Picture"), SELECT_PICTURE);
					}
				});
        _path = Environment.getExternalStorageDirectory() + "/make_machine_example.jpg";

		tess_init();
    }

	private void tess_init() {
		tessBaseAPI = new TessBaseAPI();

		String path = Environment.getExternalStoragePublicDirectory(
				Environment.DIRECTORY_DOWNLOADS).toString();
		tessBaseAPI.setDebug(true);
		tessBaseAPI.init(path, "eng"); //Init the Tess with the trained data file, with english language

		//For example if we don't want tox detect numbers
        tessBaseAPI.setVariable(TessBaseAPI.VAR_CHAR_BLACKLIST, "1234567890");
	}
    
    public class ButtonClickHandler implements View.OnClickListener 
    {
    	public void onClick( View view ){
    		Log.i("MakeMachine", "ButtonClickHandler.onClick()" );
    		startCameraActivity();
    	}
    }
    
    protected void startCameraActivity()
    {
    	Log.i("MakeMachine", "startCameraActivity()" );
    	File file = new File( _path );
    	Uri outputFileUri = Uri.fromFile( file );
    	
    	Intent intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE );
    	intent.putExtra( MediaStore.EXTRA_OUTPUT, outputFileUri );
    	
    	startActivityForResult( intent, 0 );
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) 
    {	
    	Log.i( "MakeMachine", "resultCode: " + resultCode );
    	switch( resultCode )
    	{
    		case 0:
    			Log.i( "MakeMachine", "User cancelled" );
    			break;
    			
    		case -1:
				if (requestCode == SELECT_PICTURE) {
					Uri selectedImage = data.getData();
					String[] filePathColumn = {MediaStore.Images.Media.DATA};

					Cursor cursor = getContentResolver().query(
							selectedImage, filePathColumn, null, null, null);
					cursor.moveToFirst();

					int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
					String filePath = cursor.getString(columnIndex);
					cursor.close();
					_path = filePath;

					onPhotoTaken();
					break;
				}
    			onPhotoTaken();
    			break;
    	}
    }

	public String getPath(Uri uri) {
		String selectedImagePath;
		//1:MEDIA GALLERY --- query from MediaStore.Images.Media.DATA
		String[] projection = { MediaStore.Images.Media.DATA };
		Cursor cursor = managedQuery(uri, projection, null, null, null);
		if(cursor != null){
			int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
			cursor.moveToFirst();
			selectedImagePath = cursor.getString(column_index);
		}else{
			selectedImagePath = null;
		}

		if(selectedImagePath == null){
			//2:OI FILE Manager --- call method: uri.getPath()
			selectedImagePath = uri.getPath();
		}
		return selectedImagePath;
	}

	protected void onPhotoTaken()
	{
		Log.i( "MakeMachine", "onPhotoTaken" );

		_taken = true;

		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inSampleSize = 4;
		Log.i("OnPhotoTaken", _path);
		Bitmap bitmap = BitmapFactory.decodeFile( _path, options );

		Mat mRgba = textDetection(bitmap);
		displayImage(mRgba);

	}

	protected Mat rotate(Mat src, double angle, Size size)
	{
		Mat dst = new Mat();
		Point pt = new Point(src.width()/2, src.height()/2);
		Mat r = Imgproc.getRotationMatrix2D(pt, angle, 1.0);
		double[] m = new double[r.rows() * r.cols()];
		// calculate center shift
		r.get(0,0,m);
		double new_pt_x = m[0] * pt.x + m[1]*pt.y;
		double new_pt_y = m[3] * pt.x + m[4]*pt.y;
		m[2] = -(new_pt_x - size.width/2);
		m[5] = -(new_pt_y - size.height/2);
		r.put(0,0,m);
		Imgproc.warpAffine(src, dst, r, size);
		return dst;
	}

	protected int estimateOrientation(Mat mGray) {
		Mat lines = new Mat();
		Imgproc.HoughLinesP(mGray, lines, 1, Math.PI/180, 20, 40, 5);
		int[] angles = new int[180];
		for (int i = 0; i < lines.cols(); i++) {
			double[] vec = lines.get(0, i);
			double x1 = vec[0];
			double y1 = vec[1];
			double x2 = vec[2];
			double y2 = vec[3];
			Point p1 = new Point(x1, y1);
			Point p2 = new Point(x2, y2);

			Core.line(mRgba_hough, p1, p2, new Scalar(255, 0, 0));
			int angle = (int)(Math.atan((y2-y1)/(x2-x1)) * 180/Math.PI);
			angle += 90;
			angle = Math.min(angle, 179);
			angles[angle]++;
		}
		int maxCount = 0, idx = 0;
		for (int i = 0; i < 180; i++) {
			if (angles[i] > maxCount) {
				idx = i;
				maxCount = angles[i];
			}
		}
		return idx - 90;
	}

	Size rotateSize(Size oldsize, int angle) {
		double theta = angle * Math.PI/180;
		double px1 = oldsize.width/2;
		double py1 = oldsize.height/2;
		double px2 = px1;
		double py2 = -py1;
		double px3 = -px1;
		double py3 = py1;
		double px4 = -px1;
		double py4 = -py1;
		double[] pxs = {px1, px2, px3, px4};
		double[] pys = {py1, py2, py3, py4};
		double[] px_new = new double[4];
		double[] py_new = new double[4];
		double maxX = 0, maxY = 0;
		for (int i = 0; i < 4; i++) {
			px_new[i] = pxs[i] * Math.cos(theta) + pys[i] * Math.sin(theta);
			py_new[i] = -pxs[i] * Math.sin(theta) + pys[i] * Math.cos(theta);
			if (Math.abs(px_new[i]) > maxX) {
				maxX = Math.abs(px_new[i]);
			}
			if (Math.abs(py_new[i]) > maxY) {
				maxY = Math.abs(py_new[i]);
			}
		}
		return new Size((int)Math.round(2*maxX), (int)Math.round(2*maxY));
	}

	protected Mat textDetection(Bitmap bitmap) {
		Mat mRgba = new Mat();
		Mat mGray = new Mat();
		Utils.bitmapToMat(bitmap, mRgba);
		Imgproc.cvtColor(mRgba, mGray, Imgproc.COLOR_RGB2GRAY);

		mRgba_original = new Mat();
		mRgba_rotation = new Mat();
		mRgba_binarize = new Mat();
		mRgba_contour = new Mat();
		mRgba_detected = new Mat();
		mRgba_erased = new Mat();
		mRgba_trans = new Mat();
		mRgba_hough = new Mat();
		mRgba_thresh = new Mat();
		mRgba_blurring = new Mat();
		mRgba_blurringh = new Mat();
		mRgba_blurringv = new Mat();
		mRgba_noiserm = new Mat();

		mRgba.copyTo(mRgba_original);


		// adaptive threshholding
		Imgproc.adaptiveThreshold(
				mGray,
				mGray,
				255,
				Imgproc.ADAPTIVE_THRESH_MEAN_C,
				Imgproc.THRESH_BINARY_INV,
				91,
				40
		);
		mGray.copyTo(mRgba_thresh);

		// small noisy region removal
		Mat mGray_tmp = new Mat();
		Mat kernelpre = new Mat(5, 5, CvType.CV_8UC1, Scalar.all(255));
		Imgproc.morphologyEx(mGray, mGray_tmp, Imgproc.MORPH_DILATE, kernelpre);
		List<MatOfPoint> contours1 = new ArrayList<MatOfPoint>();
		Mat hierarchy1 = new Mat();
		Imgproc.findContours(mGray_tmp, contours1, hierarchy1,
				Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_NONE);
		for (int idx = 0; idx < contours1.size(); idx++) {
			Rect rectan = Imgproc.boundingRect(contours1.get(idx));
			if (rectan.area() < 20 || rectan.height <= 6 || rectan.width <= 6) {
				Mat roi = new Mat(mGray, rectan);
				roi.setTo(new Scalar(0));
			}
		}
		mGray.copyTo(mRgba_noiserm);

		// estimate orientation
		mRgba.copyTo(mRgba_hough);
		int angle = estimateOrientation(mGray);
		System.out.println("angle: " + angle);
		Size rsize = rotateSize(mGray.size(), angle);
		mGray = rotate(mGray, angle, rsize);
		mRgba_rotation = rotate(mRgba_original, angle, rsize);
		mRgba_rotation.copyTo(mRgba_detected);
		mRgba_rotation.copyTo(mRgba_erased);
		mRgba_rotation.copyTo(mRgba_trans);
		mGray.copyTo(mRgba_binarize);


		// text region connection
		int kernalh = 3, kernalw = 15;
		Mat kernel = new Mat(kernalh, kernalw, CvType.CV_8UC1, Scalar.all(255));
		Imgproc.morphologyEx(mGray, mGray, Imgproc.MORPH_DILATE, kernel);
		mGray.copyTo(mRgba_blurring);
		kernalh = 1; kernalw = 7;
		kernel = new Mat(kernalh, kernalw, CvType.CV_8UC1, Scalar.all(255));
		Imgproc.morphologyEx(mGray, mGray, Imgproc.MORPH_DILATE, kernel);
		kernalh = 1; kernalw = 41;
		kernel = new Mat(kernalh, kernalw, CvType.CV_8UC1, Scalar.all(255));
		Imgproc.morphologyEx(mGray, mGray, Imgproc.MORPH_OPEN, kernel);
		mGray.copyTo(mRgba_blurringh);
		kernalh = 21; kernalw = 1;
		kernel = new Mat(kernalh, kernalw, CvType.CV_8UC1, Scalar.all(255));
		Imgproc.morphologyEx(mGray, mGray, Imgproc.MORPH_DILATE, kernel);

		kernalh = 31; kernalw = 41;
		kernel = new Mat(kernalh, kernalw, CvType.CV_8UC1, Scalar.all(255));
		Imgproc.morphologyEx(mGray, mGray, Imgproc.MORPH_OPEN, kernel);
		mGray.copyTo(mRgba_blurringv);

		kernalh = 5; kernalw = 5;
		kernel = new Mat(kernalh, kernalw, CvType.CV_8UC1, Scalar.all(255));
		Imgproc.morphologyEx(mGray, mGray, Imgproc.MORPH_DILATE, kernel);


		// detect contours
		Mat mask = Mat.zeros(mGray.size(), mRgba_original.type());
		Mat hierarchy = new Mat();
		List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
		Imgproc.findContours(mGray, contours, hierarchy,
				Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_NONE);
		List<Rect> boundingBoxes = new ArrayList<Rect>();
		Mat mRgba_texts = Mat.zeros(mGray.size(), mGray.type());
		for (int idx = 0; idx < contours.size(); idx++) {
			Rect roi = Imgproc.boundingRect(contours.get(idx));
			double imgsize = mRgba_rotation.height() * mRgba_rotation.width();
			if (roi.area() < 200 || roi.area() > 0.6 * imgsize ||
					roi.height <= kernalh || roi.width <= kernalw) {
				continue;
			}
			boundingBoxes.add(roi);
			Mat text = new Mat(mRgba_rotation, roi);
			Mat text_gray = new Mat();
			Imgproc.cvtColor(text, text_gray, Imgproc.COLOR_RGB2GRAY);
			Imgproc.adaptiveThreshold(
					text_gray,
					text_gray,
					255,
					Imgproc.ADAPTIVE_THRESH_MEAN_C,
					Imgproc.THRESH_BINARY_INV,
					91,
					22
			);

			text_gray.copyTo(mRgba_texts.submat(roi));
			Core.rectangle(mask, roi.br(), roi.tl(),
					new Scalar(255, 255, 0));
		}
		// small region removal
		Mat cont = new Mat();
		mRgba_texts.copyTo(cont);
		kernalh = 1; kernalw = 21;
		kernel = new Mat(kernalh, kernalw, CvType.CV_8UC1, Scalar.all(255));
		Imgproc.morphologyEx(cont, cont, Imgproc.MORPH_DILATE, kernel);
		List<MatOfPoint> conns = new ArrayList<MatOfPoint>();
		Imgproc.findContours(cont, conns, hierarchy,
				Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_NONE);
		double avg_size = 0;
		for (int i = 0; i < conns.size(); i++) {
			Rect conn = Imgproc.boundingRect(conns.get(i));
			avg_size += conn.area();
		}
		avg_size /= conns.size();
		for (int i = 0; i < conns.size(); i++) {
			Rect conn = Imgproc.boundingRect(conns.get(i));

			if (conn.area() < avg_size * 0.3) {
				Mat dark = Mat.zeros(conn.size(), mGray.type());
				dark.copyTo(mRgba_texts.submat(conn));
			}
		}
		mRgba_texts.copyTo(mRgba_binarize);


		// apply mask
		mask = rotate(mask, -angle, mRgba_original.size());
		Core.addWeighted(mRgba_original, 1, mask, 1, 0, mRgba);
		mRgba.copyTo(mRgba_contour);



		android.graphics.Bitmap.Config bitmapConfig =
				bitmap.getConfig();
		// set default bitmap config if none
		if(bitmapConfig == null) {
			bitmapConfig = android.graphics.Bitmap.Config.ARGB_8888;
		}
		// resource bitmaps are imutable,
		// so we need to convert it to mutable one
		bitmap = Bitmap.createBitmap(mRgba_binarize.width(), mRgba_binarize.height(),
				Bitmap.Config.ARGB_8888);
		Utils.matToBitmap(mRgba_binarize, bitmap);
		bitmap = bitmap.copy(bitmapConfig, true);


		tessBaseAPI.setImage(bitmap);
		Canvas canvas = new Canvas(bitmap);
		// new antialised Paint
		Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		// text color - #3D3D3D
		paint.setColor(Color.rgb(255, 0, 0));
		// text size in pixels
		int scale = 3;
		paint.setTextSize((int) 6 * scale);
		paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
		// text shadow
		// paint.setShadowLayer(1f, 0f, 1f, WHITE);
		List<String> words = new ArrayList<String>();
		List<Integer> xs = new ArrayList<Integer>();
		List<Integer> ys = new ArrayList<Integer>();
		List<Integer> ss = new ArrayList<Integer>();
		List<Integer> as = new ArrayList<Integer>();
		List<Integer> rs = new ArrayList<Integer>();
		List<Integer> gs = new ArrayList<Integer>();
		List<Integer> bs = new ArrayList<Integer>();
		for (int i = 0; i < boundingBoxes.size(); i++) {
			Rect rect = boundingBoxes.get(i);
			tessBaseAPI.setRectangle(rect.x, rect.y, rect.width, rect.height);
			String recognizedText = tessBaseAPI.getUTF8Text();
			Log.i("Texts", "Detected: " + recognizedText + "\t" + "mean: " + tessBaseAPI.meanConfidence());
			if (tessBaseAPI.meanConfidence() < 60) {
				continue;
			}

			Bitmap mask_bitmap = WriteFile.writeBitmap(tessBaseAPI.getThresholdedImage());
			mask = new Mat();
			Utils.bitmapToMat(mask_bitmap, mask);
			Core.bitwise_not(mask, mask);

			// Dilate the mask
			int dilation_size = 1;
			Mat kernel_dilate = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2*dilation_size + 1, 2*dilation_size+1));
			Mat mask_dilated = new Mat();
			Imgproc.dilate(mask, mask_dilated, kernel_dilate);
			int erosion_size = 1;
			Mat kernel_erode = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2*erosion_size + 1, 2*erosion_size+1));
			Mat mask_eroded = new Mat();
			Imgproc.erode(mask, mask_eroded, kernel_erode);

			ResultIterator resultIterator = tessBaseAPI.getResultIterator();
			resultIterator.begin();
			while (true) {
				int[] b = resultIterator.getBoundingBox(RIL_WORD);
				xs.add(b[0]);
				ys.add((b[1] + b[3]) / 2 + 6);
				ss.add(b[3] - b[1] + 3);

				double[] backgroundColor = new double[4];
				double[] foregroundColor = new double[4];
				int pixels_back = 0;
				int pixels_fore = 0;
				for (int row = b[1]; row < b[3]; row++) {
					for (int col = b[0]; col < b[2]; col++) {
						if (mask_dilated.get(row - rect.y, col - rect.x)[0] == 0) {
						// if (mRgba_binarize.get(row, col)[0] > 0) {
							pixels_back += 1;
							for (int n = 0; n < 4; n++) {
								backgroundColor[n] = backgroundColor[n] + mRgba_rotation.get(row, col)[n];
							}
							// Log.i("mRgba color", new Double(mRgba_rotation.get(row, col)[0]).toString());
						} else if (mask_eroded.get(row - rect.y, col - rect.x)[0] > 0) {
							pixels_fore += 1;
							for (int n = 0; n < 4; n++) {
								foregroundColor[n] = foregroundColor[n] + mRgba_rotation.get(row, col)[n];
							}
						}
					}
				}
				for (int n = 0; n < 4; n++) {
					backgroundColor[n] = backgroundColor[n] / pixels_back;
					foregroundColor[n] = foregroundColor[n] / pixels_fore;
				}


				for (int row = b[1]; row < b[3]; row++) {
					for (int col = b[0]; col < b[2]; col++) {
						if (mask_dilated.get(row - rect.y, col - rect.x)[0] > 0) {
							mRgba_erased.put(row, col, backgroundColor);
						}
					}
				}

				as.add((int) foregroundColor[3]);
				rs.add((int) foregroundColor[0]);
				gs.add((int) foregroundColor[1]);
				bs.add((int) foregroundColor[2]);
				canvas.drawText(resultIterator.getUTF8Text(RIL_WORD), b[0], b[1], paint);
				words.add(resultIterator.getUTF8Text(RIL_WORD));

				if (!resultIterator.next(RIL_WORD)) {
					break;
				}
			}
		}
		Utils.bitmapToMat(bitmap, mRgba_detected);
		Utils.matToBitmap(mRgba_erased, bitmap);
		bitmap = bitmap.copy(bitmapConfig, true);

		canvas = new Canvas(bitmap);
		List<String> results = new ArrayList<String>();
		for (int i = 0; i < words.size(); i++) {
			results.add(i, words.get(i));
		}
		/* Translate from English to Chinese. You need to register google
		translate API and then uncomment this.
		results = new ArrayList<String>();
		try {
			Translate t = new Translate.Builder(
					AndroidHttp.newCompatibleTransport()
					, com.google.api.client.json.gson.GsonFactory.getDefaultInstance(), null)
					.setApplicationName("Translator")
					.build();
			Translate.Translations.List list = t.new Translations().list(
							//Pass in list of strings to be translated
							words,
					//Target language
					"zh-CN");
			list.setKey("YOUR_GOOGLE_API_KEY_HERE");
			TranslationsListResponse response = list.execute();
			for(TranslationsResource tr : response.getTranslations()) {
				results.add(tr.getTranslatedText());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		*/
		for (int i = 0; i < words.size(); i++) {
			paint.setARGB(as.get(i), rs.get(i), gs.get(i), bs.get(i));
			paint.setTextSize((int) (ss.get(i)));
			canvas.drawText(results.get(i), xs.get(i), ys.get(i), paint);
			Log.i("Translated", results.get(i));
		}
		Utils.bitmapToMat(bitmap, mRgba_trans);
		mRgba_trans = rotate(mRgba_trans, -angle, mRgba_original.size());
		return mRgba_trans;
	}

	@Override
	public void onResume()
	{
		super.onResume();
		OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this, mLoaderCallback);
	}

	@Override
    protected void onRestoreInstanceState( Bundle savedInstanceState){
    	Log.i( "MakeMachine", "onRestoreInstanceState()");
    	if( savedInstanceState.getBoolean( Translator.PHOTO_TAKEN ) ) {
    		onPhotoTaken();
    	}
    }
    
    @Override
    protected void onSaveInstanceState( Bundle outState ) {
    	outState.putBoolean( Translator.PHOTO_TAKEN, _taken );
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		Log.i("MakeMachine", "called onCreateOptionsMenu");
		mStep0 = menu.add("Original");
		mStep7 = menu.add("Thresh");
		mStep8 = menu.add("Noise reduction");
		mStep9 = menu.add("Hough");
		mStep1 = menu.add("Rotation");
		mStep2 = menu.add("Binarize");
		mStep10 = menu.add("Blurring");
		mStep11 = menu.add("Blurring_h");
		mStep12 = menu.add("Blurring_v");
		mStep3 = menu.add("Contour box");
		mStep4 = menu.add("Detected");
		mStep5 = menu.add("Erased");
		mStep6 = menu.add("Translate");

		return true;
	}

	private void displayImage(Mat mRgba) {
		Bitmap bitmap = Bitmap.createBitmap(mRgba.width(), mRgba.height(),
				Bitmap.Config.ARGB_8888);
		Utils.matToBitmap(mRgba, bitmap);
		_image.setImageBitmap(bitmap);
		_field.setVisibility( View.GONE );
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		Log.i("MakeMachine", "called onOptionsItemSelected; selected item: " + item);
		if (!_taken) return false;
		Mat mRgba = new Mat();
		if (item == mStep0) {
			mRgba = mRgba_original;
		} else if (item == mStep1) {
			mRgba = mRgba_rotation;
		} else if (item == mStep2) {
			mRgba = mRgba_binarize;
		} else if (item == mStep3) {
			mRgba = mRgba_contour;
		} else if (item == mStep4) {
			mRgba = mRgba_detected;
		} else if (item == mStep5) {
			mRgba = mRgba_erased;
		} else if (item == mStep6) {
			mRgba = mRgba_trans;
		} else if (item == mStep7) {
			mRgba = mRgba_thresh;
		} else if (item == mStep8) {
			mRgba = mRgba_noiserm;
		} else if (item == mStep9) {
			mRgba = mRgba_hough;
		} else if (item == mStep10) {
			mRgba = mRgba_blurring;
		} else if (item == mStep11) {
			mRgba = mRgba_blurringh;
		} else if (item == mStep12) {
			mRgba = mRgba_blurringv;
		}
		displayImage(mRgba);
		return true;
	}


}
