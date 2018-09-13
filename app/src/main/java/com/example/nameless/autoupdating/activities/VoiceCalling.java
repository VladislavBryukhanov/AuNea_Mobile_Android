package com.example.nameless.autoupdating.activities;

import android.content.Intent;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.view.Gravity;
import android.view.View;

import com.example.nameless.autoupdating.R;
import com.example.nameless.autoupdating.voip.ListenVoiceStream;
import com.example.nameless.autoupdating.voip.UDPClient;
import com.example.nameless.autoupdating.voip.WriteVoiceStream;
import com.example.nameless.autoupdating.models.ClientToClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;

import java.io.IOException;

public class VoiceCalling extends AppCompatActivity {

    public static final String CALLING_STATE = "calling...";

    private DatabaseReference myRef, toRef;
    private ValueEventListener connectionListener;

    private ListenVoiceStream voiceStreamListenear;
    private WriteVoiceStream voiceWriter;
    private UDPClient client;

    private FloatingActionButton btnAccept, btnReject;
    private String jsonCtc;
    private String action;

    private Ringtone ringtone;
    private MediaPlayer beep;
    private Thread streamThread;

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_calling);

        mAuth = FirebaseAuth.getInstance();

        btnAccept = findViewById(R.id.btnAccept);
        btnReject = findViewById(R.id.btnReject);

        Intent intent = getIntent();
        final ClientToClient ctc = (ClientToClient)intent.getSerializableExtra("dialog");
        jsonCtc = new Gson().toJson(ctc);
        action = intent.getStringExtra("action");


        Query getUser = FirebaseDatabase.getInstance()
                .getReference("Users")
                .orderByChild("uid")
                .equalTo(mAuth.getUid());
        getUser.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                myRef = FirebaseDatabase.getInstance()
                        .getReference("Users")
                        .child(dataSnapshot.getKey())
                        .child("voiceCall");

                if(action.equals("call")) {
                    CoordinatorLayout.LayoutParams lp = (CoordinatorLayout.LayoutParams) btnReject.getLayoutParams();
                    lp.anchorGravity = Gravity.CENTER;
                    btnReject.setLayoutParams(lp);
                    btnAccept.setVisibility(View.GONE);

                    getDialogState(ctc.getSecondUser());
                    myRef.setValue(CALLING_STATE);
                    myRef.onDisconnect().removeValue();

                    beep = MediaPlayer.create(getBaseContext(), R.raw.beep);
                    beep.setLooping(true);
                    beep.start();
                } else {
                    getDialogState(ctc.getFirstUser());
                    Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                    ringtone = RingtoneManager.getRingtone(getApplicationContext(), notification);
                    ringtone.play();
                }
            }
            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {
                if(!dataSnapshot.child("voiceCall").exists()) {
                    closeConnection();
                }
            }
            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {}
            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {}
            @Override
            public void onCancelled(DatabaseError databaseError) {}
        });

        btnAccept.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                CoordinatorLayout.LayoutParams lp = (CoordinatorLayout.LayoutParams) btnReject.getLayoutParams();
                lp.anchorGravity = Gravity.CENTER;
                btnReject.setLayoutParams(lp);
                btnAccept.setVisibility(View.GONE);
                onAccept();
            }
        });
        btnReject.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onReject();
            }
        });
    }

    private void getDialogState(final String who) {
        Query getUser = FirebaseDatabase.getInstance()
                .getReference("Users")
                .orderByChild("uid")
                .equalTo(who);
        getUser.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for(DataSnapshot data : dataSnapshot.getChildren()) {
                    toRef = FirebaseDatabase.getInstance()
                            .getReference("Users")
                            .child(data.getKey())
                            .child("voiceCall");
                    if(action.equals("call")) {
                        toRef.setValue(mAuth.getUid());

                        toRef.addValueEventListener(connectionListener = new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot dataSnapshot) {
                                String key = dataSnapshot.getKey();
                                String value = (String)(dataSnapshot.getValue());
                                if(key.equals("voiceCall") && value != null && value.equals(CALLING_STATE)) {
                                    createConnection();
                                }
                            }
                            @Override
                            public void onCancelled(DatabaseError databaseError) {}
                        });
                        toRef.onDisconnect().removeValue();
/*                        toRef.onDisconnect().removeValue(new DatabaseReference.CompletionListener() {
                            @Override
                            public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
                                if(connectionListener != null) {
                                    toRef.removeEventListener(connectionListener);
                                }
                            }
                        });*/

                    }
                }
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {}
        });
    }

    private void onAccept() {
        myRef.setValue(CALLING_STATE).addOnCompleteListener(new OnCompleteListener<Void>() {

            @Override
            public void onComplete(@NonNull Task<Void> task) {
                createConnection();
            }
        });
    }

    private void onReject() {
        if(connectionListener != null) {
            toRef.removeEventListener(connectionListener);
        }
        if(toRef != null) {
            toRef.removeValue();
        }
        if(myRef != null) {
            myRef.removeValue();
        }
    }

    private void createConnection() {
        streamThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if(ringtone != null) {
                        ringtone.stop();
                    }
                    client = new UDPClient(UserList.voiceStreamServerIpAddress, UserList.voiceStreamServerPort);
                    final int port = client.createPrivateStream(jsonCtc);

                    if(beep != null) {
                        beep.stop();
                    }

                    Thread writerThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            voiceWriter = new WriteVoiceStream(port);
                            voiceWriter.start();

                            onReject();
                        }
                    });
                    writerThread.start();


                Thread listenerThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        voiceStreamListenear = new ListenVoiceStream(client);
                        voiceStreamListenear.start();

                        onReject();
                    }
                });
                listenerThread.start();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        streamThread.start();
    }

    private void closeConnection() {
/*        if(streamThread != null && streamThread.isAlive()) {
            streamThread.interrupt();
        }*/
        if(voiceStreamListenear != null) {
            voiceStreamListenear.stop();
        }
        if(voiceWriter != null) {
            voiceWriter.stop();
        }
        if(ringtone != null) {
            ringtone.stop();
        }
        if(beep != null) { // && beep.isPlaying()) {
            beep.stop();
//            beep.release();
//            beep = null;
        }
        if(client != null) {
            client.closeStream();
        }

        finish();
    }

    @Override
    protected void onDestroy() {
        onReject();
        super.onDestroy();
    }
}

