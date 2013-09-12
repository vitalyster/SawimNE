package ru.sawim.models;

import DrawControls.icons.Icon;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import ru.sawim.Scheme;
import sawim.forms.PrivateStatusForm;
import sawim.util.JLocale;
import protocol.Protocol;
import protocol.StatusInfo;
import ru.sawim.R;
import ru.sawim.view.StatusesView;

/**
 * Created with IntelliJ IDEA.
 * User: Gerc
 * Date: 26.01.13
 * Time: 20:26
 * To change this template use File | Settings | File Templates.
 */
public class StatusesAdapter extends BaseAdapter {

    private Context baseContext;
    private Protocol protocol;
    StatusInfo statusInfo;

    private int type;
    private int selectedItem;

    public StatusesAdapter(Context context, Protocol p, int type) {
        baseContext = context;
        protocol = p;
        statusInfo = protocol.getStatusInfo();
        this.type = type;
    }

    @Override
    public int getCount() {
        if (type == StatusesView.ADAPTER_STATUS)
            return statusInfo.applicableStatuses.length;
        else return PrivateStatusForm.statusNames(protocol).length;
    }

    @Override
    public Integer getItem(int i) {
        if (type == StatusesView.ADAPTER_STATUS)
            return Integer.valueOf(statusInfo.applicableStatuses[i]);
        else return PrivateStatusForm.statusIds(protocol)[i];
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    public void setSelectedItem(int position) {
        selectedItem = position;
    }

    @Override
    public View getView(int position, View convView, ViewGroup viewGroup) {
        ItemWrapper wr;
        View row = convView;
        if (row == null) {
            LayoutInflater inf = LayoutInflater.from(baseContext);
            row = inf.inflate(R.layout.status_item, null);
            wr = new ItemWrapper(row);
            row.setTag(wr);
        } else {
            wr = (ItemWrapper) row.getTag();
        }
        int item = getItem(position);
        wr.populateFrom(item);
        LinearLayout activeItem = (LinearLayout) row;
        if (item == selectedItem) {
            activeItem.setBackgroundColor(Scheme.getInversColor(Scheme.THEME_BACKGROUND));
        } else {
            activeItem.setBackgroundColor(Scheme.getColor(Scheme.THEME_BACKGROUND));
        }
        return row;
    }

    public class ItemWrapper {
        View item = null;
        private TextView itemStatus = null;
        private ImageView itemImage = null;

        public ItemWrapper(View item) {
            this.item = item;
            itemImage = (ImageView) item.findViewById(R.id.status_image);
            itemStatus = (TextView) item.findViewById(R.id.status);
        }

        void populateFrom(int item) {
            if (type == StatusesView.ADAPTER_STATUS) {
                Icon ic = statusInfo.getIcon((byte) item);
                itemStatus.setTextColor(Scheme.getColor(Scheme.THEME_TEXT));
                itemStatus.setText(statusInfo.getName((byte) item));
                if (ic != null) {
                    itemImage.setVisibility(ImageView.VISIBLE);
                    itemImage.setImageDrawable(ic.getImage());
                } else {
                    itemImage.setVisibility(ImageView.GONE);
                }
            } else {
                itemStatus.setText(JLocale.getString(PrivateStatusForm.statusNames(protocol)[item]));
                itemImage.setImageDrawable(PrivateStatusForm.privateStatusIcons.iconAt(item).getImage());
            }
        }
    }
}
