package ru.sawim.view;

import DrawControls.icons.Icon;
import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.*;
import android.widget.*;
import protocol.Contact;
import protocol.ContactMenu;
import protocol.Group;
import protocol.Protocol;
import ru.sawim.R;
import ru.sawim.SawimApplication;
import ru.sawim.Scheme;
import ru.sawim.activities.SawimActivity;
import ru.sawim.models.RosterAdapter;
import ru.sawim.widget.IconTabPageIndicator;
import ru.sawim.widget.MyListView;
import ru.sawim.widget.SwipeGestureListener;
import ru.sawim.widget.roster.RosterViewRoot;
import sawim.Options;
import sawim.chat.Chat;
import sawim.chat.ChatHistory;
import sawim.forms.ManageContactListForm;
import sawim.roster.RosterHelper;
import sawim.roster.TreeNode;


/**
 * Created with IntelliJ IDEA.
 * User: Gerc
 * Date: 01.04.13
 * Time: 19:58
 * To change this template use File | Settings | File Templates.
 */
public class RosterView extends Fragment implements ListView.OnItemClickListener, RosterHelper.OnUpdateRoster, Handler.Callback {

    public static final String TAG = RosterView.class.getSimpleName();

    private static final int UPDATE_BAR_PROTOCOLS = 0;
    private static final int UPDATE_PROGRESS_BAR = 1;
    private static final int UPDATE_ROSTER = 2;
    private static final int PUT_INTO_QUEUE = 3;

    private static LinearLayout barLinearLayout;
    private static IconTabPageIndicator horizontalScrollView;
    private static RosterViewRoot rosterViewLayout;
    private static ProgressBar progressBar;
    private RosterAdapter rosterAdapter;
    private MyListView rosterListView;
    private AdapterView.AdapterContextMenuInfo contextMenuInfo;
    private static Handler handler;
    //GestureDetector gestureDetector;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final FragmentActivity activity = getActivity();
        rosterListView = new MyListView(activity);
        rosterAdapter = new RosterAdapter(activity);
        LinearLayout.LayoutParams rosterListViewLayoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rosterListView.setLayoutParams(rosterListViewLayoutParams);
        rosterListView.setAdapter(rosterAdapter);
        activity.registerForContextMenu(rosterListView);
        rosterListView.setOnCreateContextMenuListener(this);
        rosterListView.setOnItemClickListener(this);
        /*gestureDetector = new GestureDetector(activity, new SwipeGestureListener(activity) {
            @Override
            protected void onSwipeToRight() {
                changeTab(false);
            }

            @Override
            protected void onSwipeToLeft() {
                changeTab(true);
            }
        });*/
        addProtocolsTabs();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        SawimApplication.setCurrentActivity((ActionBarActivity) activity);
        if (handler == null)
            handler = new Handler(this);
        if (barLinearLayout == null)
            barLinearLayout = new LinearLayout(activity);
        if (horizontalScrollView == null)
            horizontalScrollView = new IconTabPageIndicator(SawimApplication.getCurrentActivity());

        if (progressBar == null) {
            progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
            progressBar.setMax(100);
            progressBar.getProgressDrawable().setBounds(progressBar.getProgressDrawable().getBounds());
            LinearLayout.LayoutParams ProgressBarLP = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            ProgressBarLP.setMargins(30, 0, 30, 1);
            progressBar.setLayoutParams(ProgressBarLP);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        if (rosterViewLayout == null)
            rosterViewLayout = new RosterViewRoot(SawimApplication.getCurrentActivity(), progressBar, rosterListView);
        else
            ((ViewGroup) rosterViewLayout.getParent()).removeView(rosterViewLayout);
        rosterAdapter.setType(RosterHelper.getInstance().getCurrPage());
        return rosterViewLayout;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        rosterAdapter = null;
        rosterListView.setAdapter(null);
        rosterListView.setOnCreateContextMenuListener(null);
        rosterListView.setOnItemClickListener(null);
        rosterListView = null;
        contextMenuInfo = null;
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case UPDATE_BAR_PROTOCOLS:
                final int protocolCount = RosterHelper.getInstance().getProtocolCount();
                if (protocolCount > 1) {
                    for (int i = 0; i < protocolCount; ++i) {
                        Protocol protocol = RosterHelper.getInstance().getProtocol(i);
                        Drawable icon = protocol.getCurrentStatusIcon().getImage();
                        Drawable messageIcon = ChatHistory.instance.getUnreadMessageIcon(protocol);
                        if (null != messageIcon)
                            icon = messageIcon;
                        horizontalScrollView.updateTabIcon(i, icon);
                    }
                }
                break;
            case UPDATE_PROGRESS_BAR:
                final Protocol p = RosterHelper.getInstance().getCurrentProtocol();
                if (p != null) {
                    byte percent = p.getConnectingProgress();
                    if (100 != percent) {
                        progressBar.setVisibility(ProgressBar.VISIBLE);
                        progressBar.setProgress(percent);
                    } else {
                        progressBar.setVisibility(ProgressBar.GONE);
                    }
                    if (100 == percent || 0 == percent) {
                        SawimApplication.getCurrentActivity().supportInvalidateOptionsMenu();
                    }
                }
                break;
            case UPDATE_ROSTER:
                RosterHelper.getInstance().updateOptions();
                if (rosterAdapter != null) {
                    rosterAdapter.refreshList();
                }
                break;
            case PUT_INTO_QUEUE:
                if (rosterAdapter != null) {
                    rosterAdapter.putIntoQueue((Group) msg.obj);
                }
                break;
        }
        return false;
    }

    @Override
    public void updateBarProtocols() {
        if (handler == null) return;
        handler.sendEmptyMessage(UPDATE_BAR_PROTOCOLS);
    }

    @Override
    public void updateProgressBar() {
        if (handler == null) return;
        handler.sendEmptyMessage(UPDATE_PROGRESS_BAR);
    }

    @Override
    public void updateRoster() {
        if (handler == null) return;
        handler.sendEmptyMessage(UPDATE_ROSTER);
    }

    @Override
    public void putIntoQueue(final Group g) {
        if (handler == null) return;
        handler.sendMessage(Message.obtain(handler, PUT_INTO_QUEUE, g));
    }

    public void update() {
        updateRoster();
        updateBarProtocols();
        updateProgressBar();
    }

    private void initBar() {
        boolean isShowTabs = RosterHelper.getInstance().getProtocolCount() > 1;
        SawimApplication.getActionBar().setDisplayShowTitleEnabled(!isShowTabs);
        SawimApplication.getActionBar().setDisplayShowHomeEnabled(!isShowTabs);
        SawimApplication.getActionBar().setDisplayUseLogoEnabled(!isShowTabs);
        SawimApplication.getActionBar().setDisplayHomeAsUpEnabled(false);
        SawimApplication.getActionBar().setDisplayShowCustomEnabled(isShowTabs);
        SawimApplication.getCurrentActivity().setTitle(R.string.app_name);
        if (SawimApplication.isManyPane()) {
            ChatView chatView = (ChatView) SawimApplication.getCurrentActivity().getSupportFragmentManager()
                    .findFragmentById(R.id.chat_fragment);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
            LinearLayout.LayoutParams horizontalScrollViewLP = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
            horizontalScrollViewLP.weight = 2;
            horizontalScrollView.setLayoutParams(horizontalScrollViewLP);
            barLinearLayout.removeAllViews();
            barLinearLayout.setLayoutParams(layoutParams);
            barLinearLayout.addView(horizontalScrollView);
            chatView.removeTitleBar();
            barLinearLayout.addView(chatView.getTitleBar());
            SawimApplication.getActionBar().setCustomView(barLinearLayout);
        } else {
            if (horizontalScrollView.getParent() != null)
                ((ViewGroup) horizontalScrollView.getParent()).removeView(horizontalScrollView);
            SawimApplication.getActionBar().setCustomView(horizontalScrollView);
        }
    }

    private void addProtocolsTabs() {
        final int protocolCount = RosterHelper.getInstance().getProtocolCount();
        horizontalScrollView.removeAllTabs();
        horizontalScrollView.setOnTabSelectedListener(null);
        if (protocolCount > 1) {
            horizontalScrollView.setOnTabSelectedListener(new IconTabPageIndicator.OnTabSelectedListener() {
                @Override
                public void onTabSelected(int position) {
                    RosterHelper.getInstance().setCurrentItemProtocol(position);
                    update();
                    final Toast toast = Toast.makeText(SawimApplication.getCurrentActivity(), RosterHelper.getInstance().getProtocol(position).getUserId(), Toast.LENGTH_SHORT);
                    toast.show();
                    Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            toast.cancel();
                        }
                    }, 500);
                    SawimApplication.getCurrentActivity().supportInvalidateOptionsMenu();
                }
            });
            for (int i = 0; i < protocolCount; ++i) {
                Protocol protocol = RosterHelper.getInstance().getProtocol(i);
                Drawable icon = null;
                Icon statusIcon = protocol.getCurrentStatusIcon();
                Drawable messageIcon = ChatHistory.instance.getUnreadMessageIcon(protocol);
                if (statusIcon != null)
                    icon = statusIcon.getImage();
                if (null != messageIcon)
                    icon = messageIcon;
                if (icon != null)
                    horizontalScrollView.addTab(i, icon);
            }
            horizontalScrollView.setCurrentItem(RosterHelper.getInstance().getCurrentItemProtocol());
        }
    }

    private void changeTab(boolean next) {
        final ActionBar actionBar = SawimApplication.getActionBar();
        final int tabCount = actionBar.getTabCount();
        int position = actionBar.getSelectedNavigationIndex();
        position = next
                ? (position < tabCount - 1 ? position + 1 : 0)
                : (position > 0 ? position - 1 : tabCount - 1);
        if (position >= 0 && position < tabCount) {
            actionBar.setSelectedNavigationItem(position);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        resume();
        if (!SawimApplication.isManyPane() && Scheme.isChangeTheme(Options.getInt(Options.OPTION_COLOR_SCHEME))) {
            ((SawimActivity)SawimApplication.getCurrentActivity()).recreateActivity();
        }
    }

    public void resume() {
        SawimApplication.setCurrentActivity((ActionBarActivity) getActivity());
        initBar();
        if (RosterHelper.getInstance().getProtocolCount() > 0) {
            RosterHelper.getInstance().setCurrentContact(null);
            RosterHelper.getInstance().setOnUpdateRoster(this);
            if (SawimApplication.returnFromAcc) {
                SawimApplication.returnFromAcc = false;
                if (RosterHelper.getInstance().getCurrentProtocol().getContactItems().size() == 0 && !RosterHelper.getInstance().getCurrentProtocol().isConnecting())
                    Toast.makeText(SawimApplication.getCurrentActivity(), R.string.press_menu_for_connect, Toast.LENGTH_LONG).show();
                addProtocolsTabs();
            }
            update();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        RosterHelper.getInstance().setOnUpdateRoster(null);
    }

    public RosterAdapter getRosterAdapter() {
        return rosterAdapter;
    }

    private void openChat(Protocol p, Contact c, boolean allowingStateLoss) {
        c.activate(p);
        if (!SawimApplication.isManyPane()) {
            ChatView chatView = new ChatView();
            chatView.initChat(p, c);
            FragmentTransaction transaction = SawimApplication.getCurrentActivity().getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.fragment_container, chatView, ChatView.TAG);
            transaction.addToBackStack(null);
            if (allowingStateLoss)
                transaction.commitAllowingStateLoss();
            else
                transaction.commit();
        } else {
            ChatView chatViewTablet = (ChatView) SawimApplication.getCurrentActivity().getSupportFragmentManager()
                .findFragmentById(R.id.chat_fragment);
            chatViewTablet.pause(chatViewTablet.getCurrentChat());
            if (c != null) {
                chatViewTablet.openChat(p, c);
                chatViewTablet.resume(chatViewTablet.getCurrentChat());
            }
        }
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
        if (RosterHelper.getInstance().getCurrPage() == RosterHelper.ACTIVE_CONTACTS) {
            Object o = rosterAdapter.getItem(position);
            if (o instanceof Chat) {
                Chat chat = (Chat) o;
                openChat(chat.getProtocol(), chat.getContact(), false);
                if (SawimApplication.getCurrentActivity().getSupportFragmentManager()
                        .findFragmentById(R.id.chat_fragment) != null)
                    update();
            }
        } else {
            TreeNode item = (TreeNode) rosterAdapter.getItem(position);
            if (item.isContact()) {
                openChat(RosterHelper.getInstance().getCurrentProtocol(), ((Contact) item), false);
                if (SawimApplication.getCurrentActivity().getSupportFragmentManager()
                        .findFragmentById(R.id.chat_fragment) != null)
                    update();
            } else if (item.isGroup()) {
                Group group = (Group) item;
                group.setExpandFlag(!group.isExpanded());
                updateRoster();
            }
        }

    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        contextMenuInfo = (AdapterView.AdapterContextMenuInfo) menuInfo;
        if (RosterHelper.getInstance().getCurrPage() == RosterHelper.ACTIVE_CONTACTS) {
            Object o = rosterAdapter.getItem(contextMenuInfo.position);
            if (o instanceof Chat) {
                Chat chat = (Chat) o;
                new ContactMenu(chat.getProtocol(), chat.getContact()).getContextMenu(menu);
            }
        } else {
            TreeNode node = (TreeNode) rosterAdapter.getItem(contextMenuInfo.position);
            Protocol p = RosterHelper.getInstance().getCurrentProtocol();
            if (node.isContact()) {
                new ContactMenu(p, (Contact) node).getContextMenu(menu);
                return;
            }
            if (node.isGroup()) {
                if (p.isConnected()) {
                    new ManageContactListForm(p, (Group) node).showMenu(SawimApplication.getCurrentActivity());
                }
            }
        }
    }

    @Override
    public boolean onContextItemSelected(final android.view.MenuItem item) {
        AdapterView.AdapterContextMenuInfo menuInfo = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        if (menuInfo == null)
            menuInfo = contextMenuInfo;
        if (RosterHelper.getInstance().getCurrPage() == RosterHelper.ACTIVE_CONTACTS) {
            Object o = rosterAdapter.getItem(menuInfo.position);
            if (o instanceof Chat) {
                Chat chat = (Chat) o;
                contactMenuItemSelected(chat.getContact(), item);
            }
        } else {
            TreeNode node = (TreeNode) rosterAdapter.getItem(menuInfo.position);
            if (node == null) return false;
            if (node.isContact()) {
                contactMenuItemSelected((Contact) node, item);
                return true;
            }
        }
        return super.onContextItemSelected(item);
    }

    private void contactMenuItemSelected(final Contact c, final android.view.MenuItem item) {
        Protocol p = RosterHelper.getInstance().getCurrentProtocol();
        if (RosterHelper.getInstance().getCurrPage() == RosterHelper.ACTIVE_CONTACTS)
            p = c.getProtocol();
        new ContactMenu(p, c).doAction(item.getItemId());
    }
}