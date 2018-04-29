package com.example.nameless.autoupdating;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.support.annotation.Nullable;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by nameless on 11.04.18.
 */

public class NotifyServiceClass {

    private Context ma;

    private FirebaseDatabase database;
    private DatabaseReference myRef;
    private Map<String, Boolean> timeOut; //первым childAdded всегда будет последнее отправленное нам сообщение во время диалога, пропускаем его

    private ChildEventListener newMsgListener;
    private Query refToListener;

    public NotifyServiceClass( Context ma) {
        this.ma = ma;

        timeOut = new HashMap<>();
        database = FirebaseDatabase.getInstance();
        myRef = database.getReference("Messages");

        myRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot dlg : dataSnapshot.getChildren()) {
                    String myListener =   String.valueOf(dlg.child("listener1").getValue());
                    String foreignListener =   String.valueOf(dlg.child("listener2").getValue());

                    if (myListener.equals(Authentification.myAcc.getLogin())
                            || foreignListener.equals(Authentification.myAcc.getLogin())) {

                        if (!myListener.equals(Authentification.myAcc.getLogin()) ) {
                            foreignListener = myListener;
                        }

                        final String foreignListenerTmp = foreignListener;

                        refToListener = myRef.child(dlg.getKey()).child("content").limitToLast(1);
//                        myRef.child(dlg.getKey()).child("content").limitToLast(1).addChildEventListener(newMsgListener = new ChildEventListener() {
                        refToListener.addChildEventListener(newMsgListener = new ChildEventListener() {
                            @Override
                            public void onChildAdded(DataSnapshot dataSnapshot, String s) {

                                Message newMsg = dataSnapshot.getValue(Message.class);
                                if ((newMsg.getTo()).equals((Authentification.myAcc.getLogin()))) {
                                    if(!timeOut.get(foreignListenerTmp)) {
                                        timeOut.put(foreignListenerTmp, true);
                                    } else {
                                        sendNotify(newMsg);
                                    }
                                }
                            }

                            @Override
                            public void onChildChanged(DataSnapshot dataSnapshot, String s) {

                            }

                            @Override
                            public void onChildRemoved(DataSnapshot dataSnapshot) {

                            }

                            @Override
                            public void onChildMoved(DataSnapshot dataSnapshot, String s) {

                            }

                            @Override
                            public void onCancelled(DatabaseError databaseError) {

                            }
                        });
//                        refToListener.removeEventListener(newMsgListener);

                        timeOut.put(foreignListener, false);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    public void sendNotify(Message msg) {

        Intent notificationIntent = new Intent(ma, Chat.class);
        notificationIntent.putExtra("to", msg.getWho());
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent intent = PendingIntent.getActivity(ma, 0,
                notificationIntent, 0);

        NotificationManager notificationManager = (NotificationManager) ma.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification notification = new Notification.Builder(ma)
                .setContentTitle(msg.getWho())
                .setContentText(msg.getContent())
                .setContentIntent(intent)
                .setSmallIcon(R.drawable.send2)
                .setSound(Uri.parse("android.resource://" + ma
                        .getPackageName() + "/" + R.raw.notify)).build();
        notification.flags |= Notification.FLAG_AUTO_CANCEL;

        notificationManager.notify(1, notification);
    }
}