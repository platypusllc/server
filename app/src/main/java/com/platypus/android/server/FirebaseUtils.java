package com.platypus.android.server;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;


/**
 * Utility classes for interacting with Firebase.
 */
public class FirebaseUtils {
    /**
     * Tag used for Android log records.
     */
    private static final String TAG = FirebaseUtils.class.getSimpleName();

    private static final Object mDatabaseLock = new Object();
    private static FirebaseDatabase mDatabase = null;

    /**
     * Retrieves a reference to the Firebase Database with persistence enabled.
     * <p/>
     * This deals with interference from the crash-reporting subsystem while still allowing
     * persistence to be enabled before any database references are created.
     *
     * @return a Firebase database instance.
     */
    public static FirebaseDatabase getDatabase() {
        // Create a reference to the database if one does not already exist.
        synchronized (mDatabaseLock) {
            if (mDatabase == null) {
                mDatabase = FirebaseDatabase.getInstance();
                mDatabase.setPersistenceEnabled(true);
            }
        }

        // Return the current database reference.
        return mDatabase;
    }

    /**
     * Attempts to login using the current authentication token credential when it is available.
     *
     * @param context parent Context to use for the shared preferences for login information
     */
    public static void firebaseSignin(final Context context,
                                      final OnSuccessListener<AuthResult> success,
                                      final OnFailureListener failure) {

        // Check if we are already logged in:  if so, simply return.
        final FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            success.onSuccess(new AuthResult() {
                @Override
                public FirebaseUser getUser() {
                    return user;
                }
            });
        }

        // Get the current auth token credentials and exit if empty.
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        final String token = preferences.getString("pref_cloud_token", "");
        if (token.trim().isEmpty()) {
            failure.onFailure(new FirebaseAuthInvalidUserException("No user provided.", "No user provided."));
            return;
        }

        // Attempt to log into Firebase with the current token credential.
        // This links directly to a low-priority service account that can only be used for logs.
        FirebaseAuth.getInstance()
                .signInWithEmailAndPassword("vehicle@senseplatypus.com", token)
                .addOnSuccessListener(new OnSuccessListener<AuthResult>() {
                    @Override
                    public void onSuccess(final AuthResult authResult) {
                        Log.i(TAG, "Logged into Platypus Cloud as " +
                                authResult.getUser().getDisplayName());

                        // Call child handler if it was provided.
                        if (success != null)
                            success.onSuccess(authResult);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Failed to log into Platypus Cloud.", e);
                        Toast.makeText(context,
                                "Unable to authenticate to Platypus Cloud. " +
                                        "Please contact help@senseplatypus.com to get a new token.",
                                Toast.LENGTH_LONG).show();

                        // Call child handler if it was provided.
                        if (failure != null)
                            failure.onFailure(e);
                    }
                });
    }
}