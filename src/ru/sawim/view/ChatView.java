package ru.sawim.view;


import DrawControls.icons.Icon;
import DrawControls.icons.ImageList;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import protocol.Contact;
import protocol.Protocol;
import protocol.jabber.*;
import ru.sawim.General;
import ru.sawim.R;
import ru.sawim.models.MessagesAdapter;
import ru.sawim.models.MucUsersAdapter;
import sawim.SawimUI;
import sawim.chat.Chat;
import sawim.chat.ChatHistory;
import sawim.chat.MessData;
import sawim.cl.ContactList;
import sawim.ui.base.Scheme;

import java.util.Hashtable;

/**
 * Created with IntelliJ IDEA.
 * User: Gerc
 * Date: 24.01.13
 * Time: 20:30
 * To change this template use File | Settings | File Templates.
 */
public class ChatView extends Fragment implements AbsListView.OnScrollListener, General.OnUpdateChat {

    public static final String PASTE_TEXT = "ru.sawim.PASTE_TEXT";
    public static final int MENU_COPY_TEXT = 200;
    private static Hashtable<String, Integer> positionHash = new Hashtable<String, Integer>();
    private final TextView.OnEditorActionListener enterListener = new TextView.OnEditorActionListener() {
        public boolean onEditorAction(TextView exampleView, int actionId, KeyEvent event) {
            if (isDone(actionId)) {
                if ((null == event) || (event.getAction() == KeyEvent.ACTION_DOWN)) {
                    send();
                    return true;
                }
            }
            return false;
        }
    };
    private Chat chat;
    private MyListView chatListView;
    private EditText messageEditor;
    private boolean sendByEnter;
    private MessagesAdapter adapter;
    private Protocol protocol;
    private Contact currentContact;
    private BroadcastReceiver textReceiver;
    private LinearLayout sidebar;
    private ImageView usersImage;
    private MucUsersAdapter usersAdapter;
    private ListView nickList;
    private ImageView chatsImage;
    private TextView contactName;
    private TextView contactStatus;
    private LinearLayout chatBarLayout;
    private LinearLayout chat_viewLayout;
    private TextWatcher textWatcher = new TextWatcher() {
        private String previousText;
        private int lineCount = 0;

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            if (sendByEnter) {
                previousText = s.toString();
            }
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (sendByEnter && (start + count <= s.length()) && (1 == count)) {
                boolean enter = ('\n' == s.charAt(start));
                if (enter) {
                    messageEditor.setText(previousText);
                    messageEditor.setSelection(start);
                    send();
                }
            }
            if (lineCount != messageEditor.getLineCount()) {
                lineCount = messageEditor.getLineCount();
                messageEditor.requestLayout();
            }
        }

        @Override
        public void afterTextChanged(Editable editable) {
        }
    };

    @Override
    public void onActivityCreated(Bundle b) {
        super.onActivityCreated(b);
        chatBarLayout.setBackgroundColor(General.getColorWithAlpha(Scheme.THEME_CAP_BACKGROUND));
        chat_viewLayout.setBackgroundColor(General.getColorWithAlpha(Scheme.THEME_BACKGROUND));
        usersImage.setImageBitmap(General.iconToBitmap(ImageList.createImageList("/participants.png").iconAt(0)));
        usersImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sidebar.setVisibility(sidebar.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                messageReceived();
            }
        });
        chatsImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                forceGoToChat();
            }
        });
        updateChatsIcon();
        registerReceivers();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.chat, container, false);
        chat_viewLayout = (LinearLayout) v.findViewById(R.id.chat_view);
        chatBarLayout = (LinearLayout) v.findViewById(R.id.chat_bar);
        usersImage = (ImageView) v.findViewById(R.id.usersImage);
        contactName = (TextView) v.findViewById(R.id.item_name);
        contactStatus = (TextView) v.findViewById(R.id.item_description);
        chatsImage = (ImageView) v.findViewById(R.id.chatsImage);
        return v;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        menu.add(Menu.FIRST, MENU_COPY_TEXT, 0, android.R.string.copy);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        switch (item.getItemId()) {
            case MENU_COPY_TEXT:
                MessData md = chat.getMessData().get(info.position);
                if (null == md) {
                    return false;
                }
                String msg = md.getText();
                if (md.isMe()) {
                    msg = "*" + md.getNick() + " " + msg;
                }
                SawimUI.setClipBoardText(md.isIncoming(), md.getNick(), md.strTime, msg);
        }

        return true;
    }

    private void registerReceivers() {
        textReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                setTextOnEditor(i.getExtras().getString("text"));
            }
        };
        getActivity().registerReceiver(textReceiver, new IntentFilter(PASTE_TEXT));
    }

    private void unregisterReceivers() {
        try {
            getActivity().unregisterReceiver(textReceiver);
        } catch (java.lang.IllegalArgumentException e) {
        }
    }

    private void setTextOnEditor(String text) {
        int pos = messageEditor.getSelectionEnd();
        String oldText = messageEditor.getText().toString();
        String newText = oldText.substring(0, pos) + text + oldText.substring(pos);
        messageEditor.setText(newText);
        messageEditor.setSelection(messageEditor.getText().length());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        General.getInstance().setOnUpdateChat(null);
        if (chat == null) return;
        chat.resetUnreadMessages();
        chat.setVisibleChat(false);
        unregisterReceivers();
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.e("ChatView", "onPause()");
        if (chat == null) return;
        //if (!chatListView.isScroll())
        addLastPosition(chat.getContact().getUserId(), chatListView.getFirstVisiblePosition());
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.e("ChatView", "onResume()");
        resume(chat);
    }

    private void resume(final Chat chat) {
        if (chat == null) return;
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                int count = chatListView.getCount();
                int unreadMessages = chat.getUnreadMessageCount();
                int lastPosition = getLastPosition(chat.getContact().getUserId()) + 1;
                if (lastPosition >= 0) {
                    chatListView.setScroll(false);
                    chatListView.setSelection(lastPosition);
                } else {
                    if (unreadMessages > 0) {
                        chatListView.setScroll(false);
                        chatListView.setSelection(count - (unreadMessages + 1));
                    } else {
                        if (chatListView.isScroll()) chatListView.setSelection(count);
                    }
                }
            }
        });
        chat.resetUnreadMessages();
        updateChatsIcon();
    }

    private void forceGoToChat() {
        addLastPosition(chat.getContact().getUserId(), chatListView.getFirstVisiblePosition());
        chat.resetUnreadMessages();
        chat.setVisibleChat(false);
        ChatHistory chatHistory = ChatHistory.instance;
        Chat current = chatHistory.chatAt(chatHistory.getPreferredItem());
        if (0 < current.getUnreadMessageCount()) {
            openChat(current.getProtocol(), current.getContact());
            resume(current);
        }
    }

    public void openChat(Protocol p, Contact c) {
        General.getInstance().setOnUpdateChat(null);
        General.getInstance().setOnUpdateChat(this);
        final Activity currentActivity = getActivity();
        protocol = p;
        currentContact = c;
        Chat prevChat = chat;
        chat = protocol.getChat(currentContact);
        adapter = new MessagesAdapter(currentActivity, chat);
        chatListView = (MyListView) currentActivity.findViewById(R.id.chat_history_list);
        chat.setVisibleChat(true);

        contactName.setTextColor(General.getColor(Scheme.THEME_CAP_TEXT));
        contactName.setText(currentContact.getName());
        contactStatus.setTextColor(General.getColor(Scheme.THEME_CAP_TEXT));
        contactStatus.setText(ContactList.getInstance().getManager().getStatusMessage(currentContact));
        messageEditor = (EditText) currentActivity.findViewById(R.id.messageBox);
        int background = General.getColorWithAlpha(Scheme.THEME_BACKGROUND);
        LinearLayout chatLayout = (LinearLayout) currentActivity.findViewById(R.id.chat_view);
        chatLayout.setBackgroundColor(General.getColorWithAlpha(Scheme.THEME_BACKGROUND));
        messageEditor.setBackgroundColor(background);
        messageEditor.setTextColor(General.getColor(Scheme.THEME_TEXT));

        nickList = (ListView) currentActivity.findViewById(R.id.muc_user_list);
        sidebar = (LinearLayout) currentActivity.findViewById(R.id.sidebar);
        sidebar.setVisibility(View.GONE);
        sidebar.setBackgroundColor(General.getColor(Scheme.THEME_BACKGROUND));
        if (currentContact instanceof JabberServiceContact && currentContact.isConference()) {
            final JabberServiceContact jabberServiceContact = (JabberServiceContact) currentContact;
            usersAdapter = new MucUsersAdapter(getActivity(), (Jabber) protocol, jabberServiceContact);
            nickList.setAdapter(usersAdapter);
            usersImage.setVisibility(View.VISIBLE);
            nickList.setVisibility(View.VISIBLE);
            nickList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                    Object o = usersAdapter.getItem(position);
                    if (o instanceof JabberContact.SubContact) {
                        JabberContact.SubContact c = (JabberContact.SubContact) o;
                        setTextOnEditor(c.resource + ", ");
                    }
                }
            });
            nickList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> adapterView, View view, final int position, long l) {
                    final Object o = usersAdapter.getItem(position);
                    if (o instanceof String) return false;
                    CharSequence[] items = new CharSequence[4];
                    items[0] = currentActivity.getString(R.string.open_private);
                    items[1] = currentActivity.getString(R.string.info);
                    items[2] = currentActivity.getString(R.string.user_statuses);
                    items[3] = currentActivity.getString(R.string.adhoc);

                    AlertDialog.Builder builder = new AlertDialog.Builder(currentActivity);
                    builder.setTitle(currentContact.getName());
                    builder.setItems(items, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            String nick = usersAdapter.getCurrentSubContact(o);
                            switch (which) {
                                case 0:
                                    String jid = Jid.realJidToSawimJid(jabberServiceContact.getUserId() + "/" + nick);
                                    JabberServiceContact c = (JabberServiceContact) protocol.getItemByUIN(jid);
                                    if (null == c) {
                                        c = (JabberServiceContact) protocol.createTempContact(jid);
                                        protocol.addTempContact(c);
                                    }
                                    openChat(protocol, c);
                                    break;
                                case 1:
                                    protocol.showUserInfo(usersAdapter.getContactForVCard(nick));
                                    break;
                                case 2:
                                    protocol.showStatus(usersAdapter.getPrivateContact(nick));
                                    break;
                                case 3:
                                    JabberContact.SubContact subContact = jabberServiceContact.getExistSubContact(nick);
                                    AdHoc adhoc = new AdHoc((Jabber) protocol, jabberServiceContact);
                                    adhoc.setResource(subContact.resource);
                                    adhoc.show();
                                    break;
                            }
                        }
                    });
                    builder.create().show();
                    return false;
                }
            });
            if (sidebar.getVisibility() == View.VISIBLE) {
                sidebar.setVisibility(View.VISIBLE);
            } else {
                sidebar.setVisibility(View.GONE);
            }
        } else {
            usersImage.setVisibility(View.GONE);
            nickList.setVisibility(View.GONE);
        }

        ImageButton smileButton = (ImageButton) currentActivity.findViewById(R.id.input_smile_button);
        smileButton.setBackgroundColor(background);
        smileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new SmilesView().show(getActivity().getSupportFragmentManager(), "onContextItemSelected-smile");
            }
        });
        ImageButton sendButton = (ImageButton) currentActivity.findViewById(R.id.input_send_button);
        sendButton.setBackgroundColor(background);
        sendByEnter = (null == sendButton);
        if (null != sendButton) {
            sendButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    send();
                }
            });
        }
        if (sendByEnter) {
            messageEditor.setImeOptions(EditorInfo.IME_ACTION_SEND);
            messageEditor.setOnEditorActionListener(enterListener);
        }
        messageEditor.addTextChangedListener(textWatcher);
        chatListView.setFocusable(true);
        chatListView.setCacheColorHint(0x00000000);
        chatListView.setOnScrollListener(this);
        chatListView.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);
        chatListView.setStackFromBottom(true);
        chatListView.setAdapter(adapter);
        chatListView.setOnCreateContextMenuListener(this);
        chatListView.setOnItemClickListener(new ListView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                MessData msg = adapter.getItem(position);
                setText("");
                setText(chat.onMessageSelected(msg));
            }
        });
        /*if (prevChat != null) {
            resume(prevChat);
            Log.e("ChatView", "prevChat = " + prevChat.getContact().getName());
            addLastPosition(prevChat.getContact().getUserId(), chatListView.getFirstVisiblePosition());
            prevChat.resetUnreadMessages();
            prevChat.setVisibleChat(false);
        }*/

    }

    private void showKeyboard(View view) {
        Configuration conf = Resources.getSystem().getConfiguration();
        if (conf.hardKeyboardHidden != Configuration.HARDKEYBOARDHIDDEN_NO) {
            InputMethodManager keyboard = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            keyboard.showSoftInput(view, InputMethodManager.SHOW_FORCED);
        }
    }

    public void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    public void showKeyboard() {
        messageEditor.requestFocus();
        showKeyboard(messageEditor);
    }

    private void send() {
        hideKeyboard(messageEditor);
        chat.sendMessage(getText());
        resetText();
        messageReceived();
    }

    public boolean canAdd(String what) {
        String text = getText();
        if (0 == text.length()) return false;
        // more then one comma
        if (text.indexOf(',') != text.lastIndexOf(',')) return true;
        // replace one post number to another
        if (what.startsWith("#") && !text.contains(" ")) return false;
        return !text.endsWith(", ");
    }

    public void resetText() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                messageEditor.setText("");
            }
        });
    }

    public String getText() {
        return messageEditor.getText().toString();
    }

    public void setText(final String text) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String t = null == text ? "" : text;
                if ((0 == t.length()) || !canAdd(t)) {
                    messageEditor.setText(t);
                    messageEditor.setSelection(t.length());
                } else {
                    insert(t);
                }
                showKeyboard();
            }
        });
    }

    public boolean hasText() {
        return 0 < messageEditor.getText().length();
    }

    public void insert(String text) {
        int start = messageEditor.getSelectionStart();
        int end = messageEditor.getSelectionEnd();
        messageEditor.getText().replace(Math.min(start, end), Math.max(start, end),
                text, 0, text.length());
    }

    private boolean isDone(int actionId) {
        return (EditorInfo.IME_NULL == actionId)
                || (EditorInfo.IME_ACTION_DONE == actionId)
                || (EditorInfo.IME_ACTION_SEND == actionId);
    }

    private void updateChatsIcon() {
        Icon icMess = ChatHistory.instance.getUnreadMessageIcon();
        if (icMess == null) {
            chatsImage.setVisibility(ImageView.GONE);
        } else {
            chatsImage.setVisibility(ImageView.VISIBLE);
            chatsImage.setImageBitmap(General.iconToBitmap(icMess));
        }
    }

    private void messageReceived() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateChatsIcon();
                boolean scroll = chatListView.isScroll();
                if (adapter != null) {
                    if (scroll && chatListView.getCount() >= 1) {
                        chatListView.setSelection(chatListView.getCount());
                    }
                    adapter.notifyDataSetChanged();
                }
                if (usersAdapter != null)
                    usersAdapter.notifyDataSetChanged();
            }
        });
    }

    private void addLastPosition(String jid, int position) {
        positionHash.put(jid, position);
    }

    private int getLastPosition(String jid) {
        if (positionHash.containsKey(jid)) return positionHash.remove(jid);
        else return -1;
    }

    public void onScrollStateChanged(AbsListView view, int scrollState) {
    }

    public void onScroll(AbsListView view, final int firstVisibleItem, final int visibleItemCount, final int totalItemCount) {
        if (firstVisibleItem + visibleItemCount == totalItemCount) chatListView.setScroll(true);
        else chatListView.setScroll(false);
    }

    @Override
    public void updateChat() {
        messageReceived();
    }
}
