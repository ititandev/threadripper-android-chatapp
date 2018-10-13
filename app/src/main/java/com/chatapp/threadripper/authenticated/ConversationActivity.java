package com.chatapp.threadripper.authenticated;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.chatapp.threadripper.BaseActivity;
import com.chatapp.threadripper.R;
import com.chatapp.threadripper.api.ApiService;
import com.chatapp.threadripper.api.Config;
import com.chatapp.threadripper.authenticated.models.Message;
import com.chatapp.threadripper.authenticated.adapters.ConversationAdapter;
import com.chatapp.threadripper.utils.Constants;
import com.chatapp.threadripper.utils.ImageLoader;
import com.chatapp.threadripper.utils.Preferences;
import com.chatapp.threadripper.utils.ShowToast;
import com.makeramen.roundedimageview.RoundedImageView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import de.hdodenhof.circleimageview.CircleImageView;
import ua.naiksoftware.stomp.Stomp;
import ua.naiksoftware.stomp.client.StompClient;


public class ConversationActivity extends BaseActivity {

    private RecyclerView mRecyclerView;
    private ConversationAdapter mAdapter;
    private EditText edtMessage;
    private ImageButton imgBtnSend, btnAttacthChatImage;
    private CircleImageView cirImgUserAvatar;
    private View onlineIndicator;
    private RoundedImageView rivPickedImageFromMedia;

    String username, userAvatarImage;
    String uriAttachtImage;

    StompClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);

        Intent intent = getIntent();
        username = intent.getStringExtra("Username");
        userAvatarImage = intent.getStringExtra("Image");
        boolean isOnline = intent.getBooleanExtra("IsOnline", false);

        setupToolbarWithBackButton(R.id.toolbar, username);

        edtMessage = (EditText) findViewById(R.id.edtMessage);
        imgBtnSend = (ImageButton) findViewById(R.id.imgBtnSend);
        btnAttacthChatImage = (ImageButton) findViewById(R.id.btnAttacthChatImage);
        rivPickedImageFromMedia = (RoundedImageView) findViewById(R.id.rivPickedImageFromMedia);

        // Load User Avatar & Online ?
        cirImgUserAvatar = (CircleImageView) findViewById(R.id.cirImgUserAvatar);
        onlineIndicator = findViewById(R.id.onlineIndicator);

        findViewById(R.id.rlImgUserAvatar).setVisibility(View.VISIBLE);
        ImageLoader.loadUserAvatar(cirImgUserAvatar, userAvatarImage);
        if (isOnline) onlineIndicator.setVisibility(View.VISIBLE);
        else onlineIndicator.setVisibility(View.GONE);

        // Messages
        mRecyclerView = (RecyclerView) findViewById(R.id.recyclerView);

        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new ConversationAdapter(this, null);
        mRecyclerView.setAdapter(mAdapter);

        setListeners();

        fetchMessages();

        setupWebSocket();
    }

    void setupWebSocket() {
        client = Stomp.over(Stomp.ConnectionProvider.OKHTTP, Config.WEB_SOCKET_FULL_PATH);

        client.connect();

        client.topic("/topic/public").subscribe(message -> {
            String str = message.getPayload();
            Log.d("LogConversation:", "setupWebSocket: " + str);
            JSONObject json = null;
            try {
                json = new JSONObject(str);
                String type = json.getString("type");
                if (type.equals("CHAT")) {
                    // Chat message
                    String sender = json.getString("sender");
                    String content = json.getString("content");
                    if (!sender.equals(Preferences.getUsername())) { // other user
                        runOnUiThread(new Runnable() { // main thread
                            @Override
                            public void run() {
                                handleOnReceiveMessage(content);
                            }
                        });
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });
    }

    void setListeners() {

        edtMessage.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                // For the system keyboard toggle before scroll to last message
                mRecyclerView.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        scrollToBottom();
                    }
                }, 500);
                return false;
            }
        });

        imgBtnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (edtMessage.getVisibility() == View.VISIBLE) {
                    // send a message text
                    handleSendMessage();
                } else if (rivPickedImageFromMedia.getVisibility() == View.VISIBLE) {
                    // send a message image
                    handleSendAttachImage();
                }
            }
        });

        btnAttacthChatImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                handleAttachImage();
            }
        });
    }

    void handleAttachImage() {
        btnAttacthChatImage.setImageResource(R.drawable.ic_action_image_accent);
        edtMessage.setVisibility(View.GONE);

        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select picture"), Constants.RESULT_CODE_PICK_IMAGE);
    }

    void handleSendAttachImage() {
        Message item = new Message();
        item.setTime("6:00pm");
        item.setType("2");
        item.setImgUrl(uriAttachtImage);

        mAdapter.addItem(item);
        scrollToBottom();

        edtMessage.setVisibility(View.VISIBLE);
        rivPickedImageFromMedia.setImageResource(R.drawable.placeholder_image_chat);
        rivPickedImageFromMedia.setVisibility(View.GONE);
    }

    void handleSendMessage() {
        String msg = edtMessage.getText().toString().trim();
        if (msg.isEmpty()) return;

        Message item = new Message();
        item.setTime("6:00pm");
        item.setType("2");
        item.setText(msg);

        mAdapter.addItem(item);
        scrollToBottom();
        edtMessage.setText("");

        JSONObject json = new JSONObject();

        try {
            json.put("sender", Preferences.getUsername());
            json.put("content", msg);
            json.put("type", "CHAT");
        } catch (JSONException e) {
            e.printStackTrace();
        }

        client.send("/app/chat.sendMessage", json.toString()).subscribe(
                () -> Log.d("LogConversation", "Sent data!"),
                error -> Log.e("LogConversation", "Encountered error while sending data!", error)
        );
    }

    void handleOnReceiveMessage(String msg) {
        if (msg.isEmpty()) return;

        Message item = new Message();
        item.setTime("6:00pm");
        item.setType("1");
        item.setText(msg);
        item.setAvatarUser(userAvatarImage);

        mAdapter.addItem(item);
        scrollToBottom();
    }

    void fetchMessages() {
        ApiService.getInstance().getMessages(new ApiService.OnCompleteListener() {
            @Override
            public void onSuccess(ArrayList list) {
                ArrayList<Message> messages = new ArrayList<>();
                for (Object i : list) {
                    Message m = (Message) i;
                    if (m.getType().equals("1")) { // YOU
                        m.setAvatarUser(userAvatarImage);
                    }
                    messages.add(m);
                }
                mAdapter.setItemsList(messages);

                mRecyclerView.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        scrollToBottom();
                    }
                }, 500);
            }

            @Override
            public void onFailure(String errorMessage) {

            }
        });
    }

    void scrollToBottom() {
        mRecyclerView.smoothScrollToPosition(mRecyclerView.getAdapter().getItemCount() - 1);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == Constants.RESULT_CODE_PICK_IMAGE) {
            if (resultCode == RESULT_OK && data != null) {
                // ShowToast.lengthShort(this, "OK");

                handlePickImageFromMedia(data);

            } else {
                // Not do anything
                // ShowToast.lengthShort(this, "An error occurred, please try again later.");
            }

            // reset button attach image
            btnAttacthChatImage.setImageResource(R.drawable.ic_action_image);
        }
    }

    void handlePickImageFromMedia(Intent data) {
        rivPickedImageFromMedia.setVisibility(View.VISIBLE);

        Uri uri = data.getData();
        uriAttachtImage = uri.toString();
        ImageLoader.loadImageChatMessage(rivPickedImageFromMedia, uriAttachtImage);

        // Log.d("LogImage", "handlePickImageFromMedia: " + uriAttachtImage);
        // example: content://com.android.providers.media.documents/document/image%3A14109
    }
}
