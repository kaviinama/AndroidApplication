package fi.example.textrecognitionandlocation;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.location.Location;
import android.media.ExifInterface;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.text.FirebaseVisionCloudTextRecognizerOptions;
import com.google.firebase.ml.vision.text.FirebaseVisionText;
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer;
import com.google.firebase.ml.vision.text.RecognizedLanguage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;


public class MainFragment extends Fragment implements View.OnClickListener, OnMapReadyCallback {
    // Required empty public constructor
    public MainFragment() {
    }



    private Bitmap imageBitmap;
    private TextView detected_text;
    private ImageView photo_view;
    private GoogleMap mMap;
    private boolean mLocationPermissionGranted;
    private boolean mCameraPermissionGranted;
    private NavController navController;
    private String image_address;
    private String language;
    private Double longitude;
    private Double latitude;
    private Marker currentLocationMarker;
    public static final int MY_PERMISSIONS_REQUEST_LOCATION = 1;
    public static final int MY_PERMISSIONS_REQUEST_IMAGE_CAPTURE = 2;
    private ProgressBar progressBar;
    private ProgressBar locationProgressBar;
    private int counter=0;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    //initialize  view
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //setOnClickListener to buttons
        view.findViewById(R.id.button_detectText).setOnClickListener(this);
        view.findViewById(R.id.button_takePhoto).setOnClickListener(this);

        //find element from .xml to variables and sets initial values
        detected_text = view.findViewById(R.id.text_from_photo);
        detected_text.setText("Detected text");

        photo_view = view.findViewById(R.id.image_view);
        progressBar = view.findViewById(R.id.progressBar);
        progressBar.setBackgroundColor(Color.GRAY);
        locationProgressBar = view.findViewById(R.id.locationProgressBar);
        locationProgressBar.setBackgroundColor(Color.GRAY);
        view.findViewById(R.id.button_detect_place).setOnClickListener(this);
        view.findViewById(R.id.button_save).setOnClickListener(this);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.show_current_place);
        mapFragment.getMapAsync(this);

        //find nav controller
        navController = Navigation.findNavController(view);

        counter ++;
        set_image_from_camera(counter);

    }

    //sets photo from camera to ImageView
    public void set_image_from_camera(int counter){


        if(counter==1) {
            //gets argument file_name from fragment_camera
            String file_name = getArguments().getString("file_name");

            //file's absolute path without file:///
            image_address = new File(getContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES), file_name + ".png").getAbsolutePath();

            //get bitmap from .png and rotate it
            imageBitmap = decodeAndRotateFile(image_address);

            // set bitmap to photo_view
            photo_view.setImageBitmap(imageBitmap);

        }else{

            // set bitmap invisible
            photo_view.setVisibility(View.INVISIBLE);
        }
    }



    // Implement the OnClickListener callback
    public void onClick(View v) {
        Button button;
        if (v instanceof Button) {
            button = (Button) v;
            //if take photo pressed calls getCameraPermission
            if (button.getId() == R.id.button_takePhoto) {
                  getCameraPermission();
             }

            //if detect text pressed, sets progress bar visible amd calls detect_text_from_image
            if (button.getId() == R.id.button_detectText) {
                progressBar.setVisibility(View.VISIBLE);
                detect_text_from_image();
            }
            //if get location pressed calls getLocationPermission
            if (button.getId() == R.id.button_detect_place) {
                getLocationPermission();
            }
            //if save button pressed
            if (button.getId() == R.id.button_save) {
                //create new detection and save it to database
                Detection detection = new Detection(image_address, detected_text.getText().toString(), latitude, longitude);
                //get executor from DetectionDatabase class
                Executor executor=DetectionDatabase.getInstance(getContext()).getExecutor();
                //insert detection to database with runAsync function and by calling insert function
                CompletableFuture.runAsync(() -> {
                    insert(detection);
                }, executor);

                    //set photo invisible, clear text field and map and show toast to user
                    photo_view.setVisibility(View.INVISIBLE);
                    detected_text.setText("");
                    mMap.clear();
                    image_address="";
                    String msg = "Data saved." + image_address;
                    Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
             }
        }
    }




//ask location permission or calls getDeviceLocation
  private void getLocationPermission() {

        if (ContextCompat.checkSelfPermission(
                getContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_DENIED) {
            ActivityCompat
                    .requestPermissions(
                            getActivity(),
                            new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                            MY_PERMISSIONS_REQUEST_LOCATION);
        } else {
           //Toast.makeText(getContext(),"Permission Granted", Toast.LENGTH_SHORT).show();
          //  mMap.setMyLocationEnabled(true);
           getDeviceLocation();
        }
    }



  //ask camera permission or navigate to camera page
  private void getCameraPermission() {

        if (ContextCompat.checkSelfPermission(
                getContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_DENIED) {
            ActivityCompat
                    .requestPermissions(
                            getActivity(),
                            new String[]{Manifest.permission.CAMERA},
                            MY_PERMISSIONS_REQUEST_IMAGE_CAPTURE);
        } else {
            // Toast.makeText(getContext(), "Permission Granted", Toast.LENGTH_SHORT).show();
            //navigate to camera fragment
            navController.navigate(R.id.action_mainFragment_to_cameraFragment);
        }
    }


    // Checks whether user granted the location and camera permission .
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {

            //location permission
            if (requestCode == MY_PERMISSIONS_REQUEST_LOCATION ) {
                //
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                   // Showing the toast message
                   Toast.makeText(getContext(),"Camera Permission Granted", Toast.LENGTH_SHORT).show();
                   mLocationPermissionGranted=true;

                }
                else {
                    mLocationPermissionGranted=false;
                    Toast.makeText(getContext(),"Camera Permission Denied", Toast.LENGTH_SHORT).show();
                }
                return;
            }

            //camera permission
            if (requestCode == MY_PERMISSIONS_REQUEST_IMAGE_CAPTURE) {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mCameraPermissionGranted  = true;
                    Toast.makeText(getContext(), "Permission granted", Toast.LENGTH_SHORT).show();

                } else {
                    Toast.makeText(getContext(), "Permission denied", Toast.LENGTH_SHORT).show();
                    mCameraPermissionGranted = false;
                }

                return;
            }
        }




    //implements OnMapReadyCallback
    //callback is triggered when the map is ready to be used.
    //set map visible and set zoom-controls enabled
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        ///mMap.setMyLocationEnabled(true);
        mMap.getUiSettings().setZoomControlsEnabled(true);
    }


    //get location of the phone
    public void getDeviceLocation() {

        locationProgressBar.setVisibility(View.VISIBLE);
        //check SDK version of the device and if it bigger than 24 get location updates
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {

                //create new locationRequest
                LocationRequest mLocationRequest = new LocationRequest();
                // set 5 s interval
                mLocationRequest.setInterval(5000);
                // set the priority of the request to PRIORITY_HIGH_ACCURACY
                mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
                //get FusedLocationProviderClient, request location updates and
                //override the LocationCallback.onLocationResult() callback method.
                LocationServices.getFusedLocationProviderClient(getActivity())
                        .requestLocationUpdates(mLocationRequest, new LocationCallback() {
                            @Override
                            //the result is in the LocationResult list
                            public void onLocationResult(LocationResult locationResult) {
                                //get list of locations
                                List<Location> locationList = locationResult.getLocations();
                                if (locationList.size() > 0) {
                                    //Get The last location in the list
                                    Location location = locationList.get(locationList.size() - 1);

                                    //Get latitude and longitude
                                    latitude = location.getLatitude();
                                    longitude = location.getLongitude();
                                    locationProgressBar.setVisibility(View.GONE);
                                    //remove old location marker from the map
                                    if (currentLocationMarker != null) {
                                        currentLocationMarker.remove();
                                    }

                                    //Place new location marker
                                    LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                                    MarkerOptions markerOptions = new MarkerOptions();
                                    markerOptions.position(latLng);
                                    //title of the marker
                                    markerOptions.title("Lat: " + latitude + " Lon: " + longitude);
                                    //set color of the marker
                                    markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_MAGENTA));
                                    currentLocationMarker = mMap.addMarker(markerOptions);
                                    float zoomLevel = (float) 15.0;
                                    //move map camera closer to the marker with zoomLevel
                                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoomLevel));
                                    //remove location continuous updates
                                    LocationServices.getFusedLocationProviderClient(getActivity()).removeLocationUpdates(this);
                                }
                        // Location can be requested with a specific Looper Looper.myLooper().
                        //Looper is a class which is used to execute the Messages(Runnables) in a queue.
                            }


                        }, Looper.myLooper());
         }
    }


    //insert new detection to database with myDao
    private void insert(Detection detection) {
        DetectionDatabase detectionDatabase=DetectionDatabase.getInstance(getActivity());
             detectionDatabase.myDao().insert(detection);
    }


    //decode a bitmap from path and rotate it by calling the rotateBitmap -function
    public static Bitmap decodeAndRotateFile (String pathName){

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inSampleSize = 8;
        options.inJustDecodeBounds = false;

        Bitmap bitmap = BitmapFactory.decodeFile(pathName, options);

        Bitmap bmRotated = null;
        try {
            bmRotated = rotateBitmap(pathName, bitmap);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return bmRotated;
    }



    //rotate a bitmap
    public static Bitmap rotateBitmap(String filename, Bitmap bitmapOrg) throws IOException {
        Bitmap rotated_bitmap = null;
        try {
            ExifInterface exif = new ExifInterface(filename);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
            Matrix matrix = new Matrix();
            if (orientation == 6) {
                matrix.postRotate(90);
            } else if (orientation == 3) {
                matrix.postRotate(180);
            } else if (orientation == 8) {
                matrix.postRotate(270);
            }
            rotated_bitmap = Bitmap.createBitmap(bitmapOrg, 0, 0, bitmapOrg.getWidth(), bitmapOrg.getHeight(), matrix, true);
        } catch (Exception e) {
        }
        return rotated_bitmap;
    }



    //create a FirebaseVisionImage object from a Bitmap and FirebaseVisionCloudTextRecognizer with options
    public void detect_text_from_image() {

        FirebaseVisionImage visionImage = FirebaseVisionImage.fromBitmap(imageBitmap);
        FirebaseVisionCloudTextRecognizerOptions options =
                new FirebaseVisionCloudTextRecognizerOptions.Builder()
                .setLanguageHints(Arrays.asList("fi"))
                .setModelType(2)
                .build();

        FirebaseVisionTextRecognizer textDetector =
                FirebaseVision.getInstance().getCloudTextRecognizer(options);

        //pass the FirebaseVisionImage object to the FirebaseVisionTextRecognizer's processImage method.
        //then override onSuccesful and onFailure functions
        textDetector.processImage(visionImage).addOnSuccessListener(new OnSuccessListener<FirebaseVisionText>() {
            //if the detection  succeeds FirebaseVisionText texts object is passed to the success listener
            //and it will processed with displayTextFromImage function
            @Override
            public void onSuccess(FirebaseVisionText texts) {
                displayTextFromImage(texts);
            }

        //if the detection not succeeds, show a toast to the user
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(getContext(), "Error" +e.getMessage(), Toast.LENGTH_SHORT);
            }
        });

    }


    //display the detected text from image
    private void displayTextFromImage(FirebaseVisionText texts) {
        List<RecognizedLanguage> list_language = new ArrayList<RecognizedLanguage>();
        String text = "";
        //get blocs from FirebaseVisionText texts object to list
        List<FirebaseVisionText.TextBlock> blocks = texts.getTextBlocks();
        //if the list is empty set the progressbar invisible and show a toast to user
        if (blocks.size() == 0) {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(getContext(), "No text found in the image", Toast.LENGTH_SHORT);


        } else {
            // For each TextBlock and Line you can get the text recognized, confidence, detected language and the bounding
            // coordinates of the region or a rectangle frame
            //block
            for (FirebaseVisionText.TextBlock block : texts.getTextBlocks()) {
                String blockText = block.getText();
                Float blockConfidence = block.getConfidence();
                List<RecognizedLanguage> blockLanguages=
                        block.getRecognizedLanguages();
                Point[] blockCornerPoints = block.getCornerPoints();
                Rect blockFrame = block.getBoundingBox();
                //line
                for (FirebaseVisionText.Line line : block.getLines()) {
                    String lineText = line.getText();
                    Float lineConfidence = line.getConfidence();
                    List<RecognizedLanguage> lineLanguages = line.getRecognizedLanguages();
                    Point[] lineCornerPoints = line.getCornerPoints();
                    Rect lineFrame = line.getBoundingBox();

                    //get the first language from the list
                    for (RecognizedLanguage recognizedLanguage : list_language = line.getRecognizedLanguages()) {
                        language = list_language.get(0).getLanguageCode();
                    }

                    //get the lineText to the text variable
                    text = text + " " + lineText;
                    //set the progressbar invisible
                    progressBar.setVisibility(View.GONE);
                    //set detected text, confidence and language id to the TextView
                    detected_text.setText(" " +text+" "+ blockConfidence * 100 +" % , " +language);

                }

                //call the drawToImage function
                drawToImage(block);
            }
        }
    }

    //draw a bounding box and text to canvas that is associated with the bitmap
    public void drawToImage(FirebaseVisionText.TextBlock block){

        Canvas canvas = new Canvas(imageBitmap);
        Paint paint = new Paint();
        paint.setColor(Color.BLUE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        //draws bounding box
        RectF rect = new RectF(block.getBoundingBox());
        canvas.drawRect(rect, paint);

        Paint textPaint = new Paint();
        textPaint.setColor(Color.RED);
        textPaint.setTextSize(20.0f);
        textPaint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        // draw text
        canvas.drawText(block.getText()+" "+ block.getConfidence() * 100 +" %" , rect.left + 10, rect.top - 10, textPaint);

        //set bitmap to ImageView
        photo_view.setImageBitmap(imageBitmap);

    }

}


