package com.ait.aiscantestapp;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.GravityEnum;
import com.ait.aiscan.manager.Exceptions.ServerRequestErrorException;
import com.ait.aiscan.manager.api.EyeTPublic;
import com.ait.aiscan.model.CaptureParameters;
import com.ait.aiscan.model.IdentityFormat;
import com.ait.aiscan.model.Result;
import com.ait.aiscan.util.ArgDefs;
import com.ait.aiscan.util.Constants;

import com.afollestad.materialdialogs.MaterialDialog;

import static android.app.Activity.RESULT_OK;

public class user_input_fragment extends Fragment {

    private static final String LOG_TAG = user_input_fragment.class.getSimpleName();
    private static IdentityFormat eyet_identity = null;

    //  Boolean value to check if identity has been found
    private boolean identityFound = false;
    // Flag to indicate of identity check task should be cancelled
    private boolean cancelIdentityCheckTask = false;
    // Flag to indicate that identiy check task has timed out
    private boolean identityCheckTaskTimeout = false;
    // Timeout value for server response in seconds
    private static final int timeout = 10;
    // Timer to check if timeout value has been reached
    private int timer = 0;
    // EyeTLib SDK Instance
    private EyeTPublic eyeTLibPublic;

    private RadioGroup radioGroup;
    private EditText aiUID;
    private Button button_start;
    private int documentType;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Get instance of eyeTLibPublic
        eyeTLibPublic = ((aiScanApplication)getActivity().getApplicationContext()).getmEyetPublic(getActivity());
    }

    // The onCreateView method is called when Fragment should create its View object hierarchy,
    // either dynamically or via XML layout inflation.
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        // Defines the xml file for the fragment
        return inflater.inflate(R.layout.fragment_user_input, parent, false);
    }

    // This event is triggered soon after onCreateView().
    // Any view setup should occur here.  E.g., view lookups and attaching view listeners.
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        // Setup any handles to view objects here

        radioGroup = (RadioGroup) view.findViewById(R.id.radioGroup);
        radioGroup.clearCheck();

        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                RadioButton rb = (RadioButton) group.findViewById(checkedId);
                if (null != rb && checkedId > -1) {
                    Toast.makeText(getContext(), rb.getText(), Toast.LENGTH_SHORT).show();
                }

            }
        });

        aiUID = (EditText) view.findViewById(R.id.et_uid);
        aiUID.setOnKeyListener(new View.OnKeyListener()
        {
            public boolean onKey(View v, int keyCode, KeyEvent event)
            {
                if (event.getAction() == KeyEvent.ACTION_DOWN)
                {
                    switch (keyCode)
                    {
                        case KeyEvent.KEYCODE_DPAD_CENTER:
                        case KeyEvent.KEYCODE_ENTER:
                            actionStart(v);
                            return true;
                        default:
                            break;
                    }
                }
                return false;
            }
        });

        button_start = (Button) view.findViewById(R.id.button_start);
        button_start.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                actionStart(v);
            }
        });

    }

    @Override
    public void onResume(){
        super.onResume();
    }

    private void actionStart(View v) {
        RadioButton rb = (RadioButton) radioGroup.findViewById(radioGroup.getCheckedRadioButtonId());
        if (null != rb) {
            if(aiUID.getText().toString() != "") {
                Toast.makeText(getContext(), rb.getText(), Toast.LENGTH_SHORT).show();
                // Capture identity being searched for
                eyet_identity = IdentityFormat.getInstance();
                eyet_identity.setUser_id(aiUID.getText().toString());
                eyet_identity.setName("");
                documentType = radioGroup.indexOfChild(rb);
                startIdentityCheck(v);
            }else{
                // Display message to indicate that the UID has not been entered
                MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity())
                        .title("ERROR")
                        .titleColor(ContextCompat.getColor(getActivity(),R.color.md_edittext_error))
                        .titleGravity(GravityEnum.CENTER)
                        .content("Please enter uid")
                        .positiveText("OK")
                        .onPositive(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                dialog.dismiss();
                            }
                        });
                MaterialDialog dialog = builder.build();
                dialog.getContentView().setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
                dialog.show();
            }
        }else{
            // Display message to indicate that document type has not been selected
            MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity())
                    .title("ERROR")
                    .titleColor(ContextCompat.getColor(getActivity(),R.color.md_edittext_error))
                    .titleGravity(GravityEnum.CENTER)
                    .content("Please select document type")
                    .positiveText("OK")
                    .onPositive(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            dialog.dismiss();
                        }
                    });
            MaterialDialog dialog = builder.build();
            dialog.getContentView().setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            dialog.show();
        }
    }

    Handler timeoutHandler = new Handler();
    Runnable timeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (timer == timeout) {
                // Reset timer value
                timer = 0;
                // Stop asyncTask
                identityCheckTaskTimeout = true;
                // Stop handler from posing new messages
                timeoutHandler.removeCallbacksAndMessages(timeoutRunnable);
            } else {
                timer++;
                Log.e(LOG_TAG,"TIMER: " + timer);
                if (!identityCheckTaskTimeout)
                    timeoutHandler.postDelayed(timeoutRunnable, 1000);
            }
        }
    };

    private void startIdentityCheck(View v){
        identityFound = false;
        cancelIdentityCheckTask = false;
        identityCheckTaskTimeout = false;
        timer = 0;

        timeoutHandler.postDelayed(timeoutRunnable, 0);
        // Check if identity
        IdentityFormat identity = IdentityFormat.getInstance();
        identity.setUser_id(eyet_identity.getUser_id());
        identity.setClient_id("eyed");
        //eyeTLibPublic.checkIdentity(identity);

        //new fragment_eyethenticate.waitForIdentityCheckTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void)null);
        new checkIdentityTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, identity);

        // Hide soft keyboard
        if (v != null) {
            InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        }

    }

    private class checkIdentityTask extends AsyncTask<IdentityFormat,Void,Integer> {
        private IdentityFormat mIdentity = null;
        private String errorMsg = "";


        @Override
        protected Integer doInBackground(IdentityFormat... identity) {
            mIdentity = identity[0];

            Log.e(LOG_TAG,"STARTING EYEDENTITY CHECK WITH SERVER");
            try {
                if (eyeTLibPublic.checkIdentity(identity[0]) == null)
                    /* Could not find identity */
                    return 1;
                /* Identity found */
                else return 0;
            }
            catch (ServerRequestErrorException ex)
            {
                /* Couldn't find server */
                errorMsg = ex.getMessage();
                return 2;
            }

        }

        @Override
        protected void onPostExecute(Integer result) {
            identityFound = true;

            Log.e(LOG_TAG, "waitForIdentityTask: onPostExecute");

            switch (result)
            {
                /**
                 * UID was found, continue with eyethentication
                 */
                case 0: try {
                    CaptureParameters paramaters = new CaptureParameters(CaptureParameters.Camera.CAMERA_FRONT, CaptureParameters.Vibration.VIBRATION_OFF, CaptureParameters.VoiceEvent.VOICE_EVENT_OFF);

                    /**
                     * Function: Starts intent to start scan capture session
                     */
                    startScanSession(mIdentity);
                } catch (Exception ex) {
                    Log.e(LOG_TAG, "Exception: " + ex.getMessage());
                }

                    Log.i(LOG_TAG, "Identity check result for EyeThentication: UID EXISTS");
                    break;

                /**
                 * UID Does not exist, show error message
                 */
                case 1:
                    // Display message to indicate that User ID is not in the database
                    MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity())
                            .title("ERROR")
                            .titleColor(ContextCompat.getColor(getActivity(),R.color.md_edittext_error))
                            .titleGravity(GravityEnum.CENTER)
                            .content("User ID not on record")
                            .positiveText("REGISTER")
                            .onPositive(new MaterialDialog.SingleButtonCallback() {
                                @Override
                                public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                    eyet_identity.setUser_id(aiUID.getText().toString());
                                }
                            })
                            .negativeText("RETURN")
                            .onNegative(new MaterialDialog.SingleButtonCallback() {
                                @Override
                                public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {

                                }
                            });
                    MaterialDialog dialog = builder.build();
                    dialog.getContentView().setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
                    dialog.show();
                    Log.i(LOG_TAG,"Identity check result:  UID does not exist.  PLEASE TRY AGAIN.");
                    break;

                /**
                 * Couldn't get response from server, show error message
                 */
                case 2:  showRequestError(errorMsg);
                    break;


            }

        }
    }

    private void showRequestError(String msg){

        // Show dialog to indicate that timeout has occurred
        MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity())
                .title("ERROR")
                .titleColor(ContextCompat.getColor(getActivity(), R.color.md_edittext_error))
                .titleGravity(GravityEnum.CENTER)
                .content(msg)
                .positiveText("CONTINUE")
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        // Re-enable start button
                        //enableStartButton();
                    }
                })
                .canceledOnTouchOutside(false)
                .cancelable(false);
        MaterialDialog dialog = builder.build();
        dialog.getContentView().setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        dialog.show();
    }

    private void startScanSession(IdentityFormat identity){

        Intent intent = new Intent(getActivity().getApplicationContext(), com.ait.aiscan.LibraryActivity.class);
        //intent.setAction("com.ait.aiscan.LibraryActivity");
        intent.putExtra(ArgDefs.EXTRA_IDENTITY,identity);
        EyeTPublic.DocumentType dt = EyeTPublic.DocumentType.SA_ID;
        switch (documentType){
            case 0:
                dt = EyeTPublic.DocumentType.SA_ID;
                break;
            case 1:
                dt = EyeTPublic.DocumentType.SA_ID_CARD_A;
                break;
            case 2:
                dt = EyeTPublic.DocumentType.SA_ID_CARD_B;
                break;
            case 3:
                dt = EyeTPublic.DocumentType.SA_DRIVERS;
                break;
            case 4:
                dt = EyeTPublic.DocumentType.SA_PASSPORT;
                break;
        }
        intent.putExtra(ArgDefs.EXTRA_DOCUMENT_TYPE,dt);
        getActivity().startActivityForResult(intent, Constants.capture_activity_request);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        if (requestCode == Constants.capture_activity_request) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                Result result = ((Result) data.getParcelableExtra(ArgDefs.EXTRA_RESULT));
                EyeTPublic.DocumentType type = ((EyeTPublic.DocumentType) data.getSerializableExtra(ArgDefs.EXTRA_DOCUMENT_TYPE));
                IdentityFormat identity = ((IdentityFormat) data.getParcelableExtra(ArgDefs.EXTRA_IDENTITY));

                loadscreen_result(identity, result, type, resultCode);

            }else if (resultCode == Activity.RESULT_CANCELED) {
                this.onResume();
            }
        }
    }

    private void loadscreen_result(IdentityFormat identity, Result result, EyeTPublic.DocumentType type, int result_code) {
        Intent intent = new Intent(getContext(), ResultActivity.class);
        intent.putExtra(ArgDefs.EXTRA_IDENTITY, identity);
        intent.putExtra(ArgDefs.EXTRA_RESULT, result);
        intent.putExtra(ArgDefs.EXTRA_DOCUMENT_TYPE, type);
        //intent.putExtra(ArgDefs.EXTRA_RESULT_CODE,result_code);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(getContext());
        // Adds the back stack
        stackBuilder.addParentStack(ResultActivity.class);
        // Adds the Intent to the top of the stack
        stackBuilder.addNextIntent(intent);
        // Gets a PendingIntent containing the entire back stack
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        stackBuilder.startActivities();
    }
}
