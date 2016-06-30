/*
 * Copyright 2016 Davide Steduto
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.davidea.viewholders;

import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.SelectableAdapter;
import eu.davidea.flexibleadapter.helpers.ItemTouchHelperCallback;
import eu.davidea.flexibleadapter.items.IFlexible;

/**
 * Helper Class that implements:
 * <br/>- Single tap
 * <br/>- Long tap
 * <br/>- Touch for Drag and Swipe.
 * <p>You must extend and implement this class for the own ViewHolder.</p>
 *
 * @author Davide Steduto
 * @since 03/01/2016 Created
 * <br/>23/01/2016 ItemTouch with Drag&Drop, Swipe
 * <br/>26/01/2016 Constructor revisited
 * <br/>18/06/2016 StickyHeader flag is delegated to the super class (ContentViewHolder)
 */
public abstract class FlexibleViewHolder extends ContentViewHolder
		implements View.OnClickListener, View.OnLongClickListener,
		View.OnTouchListener, ItemTouchHelperCallback.ViewHolderCallback {

	private static final String TAG = FlexibleViewHolder.class.getSimpleName();

	//FlexibleAdapter is needed to retrieve listeners and item status
	protected final FlexibleAdapter mAdapter;

	//These 2 fields avoid double tactile feedback triggered by Android during the touch event
	// (Drag or Swipe), also assure the LongClick event is correctly fired for ActionMode if that
	// was the user intention.
	private boolean mLongClickSkipped = false;
	private boolean alreadySelected = false;

	//State for Dragging & Swiping actions
	protected int mActionState = ItemTouchHelper.ACTION_STATE_IDLE;

	/*--------------*/
	/* CONSTRUCTORS */
	/*--------------*/

	/**
	 * Default constructor.
	 *
	 * @param view    The {@link View} being hosted in this ViewHolder
	 * @param adapter Adapter instance of type {@link FlexibleAdapter}
	 */
	public FlexibleViewHolder(View view, FlexibleAdapter adapter) {
		this(view, adapter, false);
	}

	/**
	 * Constructor to configure the sticky behaviour of a view.
	 * <p><b>Note:</b> StickyHeader works only if the item has been declared of type
	 * {@link eu.davidea.flexibleadapter.items.IHeader}.</p>
	 *
	 * @param view         The {@link View} being hosted in this ViewHolder
	 * @param adapter      Adapter instance of type {@link FlexibleAdapter}
	 * @param stickyHeader true if the View can be a Sticky Header, false otherwise
	 */
	public FlexibleViewHolder(View view, FlexibleAdapter adapter, boolean stickyHeader) {
		super(view, adapter, stickyHeader);
		this.mAdapter = adapter;

		getContentView().setOnClickListener(this);
		getContentView().setOnLongClickListener(this);
	}

	/*--------------------------------*/
	/* CLICK LISTENERS IMPLEMENTATION */
	/*--------------------------------*/

	/**
	 * {@inheritDoc}
	 */
	@Override
	@CallSuper
	public void onClick(View view) {
		int position = getFlexibleAdapterPosition();
		if (!mAdapter.isEnabled(position)) return;
		//Experimented that, if LongClick is not consumed, onClick is fired. We skip the
		//call to the listener in this case, which is allowed only in ACTION_STATE_IDLE.
		if (mAdapter.mItemClickListener != null && mActionState == ItemTouchHelper.ACTION_STATE_IDLE) {
			if (FlexibleAdapter.DEBUG)
				Log.v(TAG, "onClick on position " + position + " mode=" + mAdapter.getMode());
			//Get the permission to activate the View from user
			if (mAdapter.mItemClickListener.onItemClick(position)) {
				//Now toggle the activation
				if (!mAdapter.isSelected(position) && itemView.isActivated() ||
						mAdapter.isSelected(position) && !itemView.isActivated()) {
					toggleActivation();
				}
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	@CallSuper
	public boolean onLongClick(View view) {
		int position = getFlexibleAdapterPosition();
		if (!mAdapter.isEnabled(position)) return false;
		if (FlexibleAdapter.DEBUG)
			Log.v(TAG, "onLongClick on position " + position + " mode=" + mAdapter.getMode());
		//If DragLongPress is enabled, then LongClick must be skipped and the listener will
		// be called in onActionStateChanged in Drag mode.
		if (mAdapter.mItemLongClickListener != null && !mAdapter.isLongPressDragEnabled()) {
			mAdapter.mItemLongClickListener.onItemLongClick(position);
			toggleActivation();
			return true;
		}
		mLongClickSkipped = true;
		return false;
	}

	/**
	 * <b>Should be used only by the Handle View!</b><br/>
	 * {@inheritDoc}
	 *
	 * @see #setDragHandleView(View)
	 */
	@Override
	public boolean onTouch(View view, MotionEvent event) {
		int position = getFlexibleAdapterPosition();
		if (!mAdapter.isEnabled(position)) return false;
		if (FlexibleAdapter.DEBUG)
			Log.v(TAG, "onTouch with DragHandleView on position " + position + " mode=" + mAdapter.getMode());
		if (MotionEventCompat.getActionMasked(event) == MotionEvent.ACTION_DOWN &&
				mAdapter.isHandleDragEnabled()) {
			//Start Drag!
			mAdapter.getItemTouchHelper().startDrag(this);
		}
		return false;
	}

	/*--------------*/
	/* MAIN METHODS */
	/*--------------*/

	/**
	 * Sets the inner view which will be used to drag the Item ViewHolder.
	 *
	 * @param view handle view
	 * @see #onTouch(View, MotionEvent)
	 */
	@CallSuper
	@SuppressWarnings("ConstantConditions")
	protected void setDragHandleView(@NonNull View view) {
		if (view != null) view.setOnTouchListener(this);
	}

	/**
	 * Allows to change and see the activation status on the ItemView and to perform object
	 * animation in it.
	 * <p><b>IMPORTANT NOTE!</b> the change of the background is visible if you added
	 * <i>android:background="?attr/selectableItemBackground"</i> on the item layout AND
	 * in the style.xml.<br/>
	 * Adapter must have a reference to its instance to check selection state.</p>
	 * <p>This must be called every time we want the activation state visible on the ItemView,
	 * for instance, after a Click (to add the item to the selection list) or after a LongClick
	 * (to activate the ActionMode) or during a Drag (to show that we enabled the Drag).</p>
	 * If you do this, it's not necessary to invalidate the row (with notifyItemChanged):
	 * In this way <i>bindViewHolder</i> is NOT called and inner Views can animate without
	 * interruption, so you can see the animation running still having the selection activated.
	 */
	@CallSuper
	protected void toggleActivation() {
		itemView.setActivated(mAdapter.isSelected(getFlexibleAdapterPosition()));
		if (itemView.isActivated() && getActivationElevation() > 0)
			ViewCompat.setElevation(itemView, getActivationElevation());
		else if (getActivationElevation() > 0)//Leave unaltered the default elevation
			ViewCompat.setElevation(itemView, 0);
	}

	/**
	 * Allows to set elevation while the view is activated.
	 * <p>Override to return desired value of elevation on this itemView.</p>
	 *
	 * @return never elevate, returns 0dp if not overridden
	 */
	public float getActivationElevation() {
		return 0f;
	}

	/**
	 * Allows to activate the itemView when Swipe event occurs.
	 * <p>This method returns always false; Extend with "return true" to Not expand or collapse
	 * this ItemView onClick events.</p>
	 *
	 * @return always false, if not overridden
	 */
	protected boolean shouldActivateViewWhileSwiping() {
		return false;
	}

	/**
	 * Allows to add and keep item selection if ActionMode is active.
	 * <p>This method returns always false; Extend with "return true" to add item to the ActionMode
	 * count.</p>
	 *
	 * @return always false, if not overridden
	 */
	protected boolean shouldAddSelectionInActionMode() {
		return false;
	}

	/*--------------------------------*/
	/* TOUCH LISTENERS IMPLEMENTATION */
	/*--------------------------------*/

	/**
	 * Here we handle the event of when the ItemTouchHelper first registers an item as being
	 * moved or swiped.
	 * <p>In this implementations, View activation is automatically handled in case of Drag:
	 * The Item will be added to the selection list if not selected yet and mode MULTI is activated.</p>
	 *
	 * @param position    the position of the item touched
	 * @param actionState one of {@link ItemTouchHelper#ACTION_STATE_SWIPE} or
	 *                    {@link ItemTouchHelper#ACTION_STATE_DRAG}.
	 */
	@Override
	@CallSuper
	public void onActionStateChanged(int position, int actionState) {
		mActionState = actionState;
		alreadySelected = mAdapter.isSelected(position);
		if (FlexibleAdapter.DEBUG)
			Log.v(TAG, "onActionStateChanged position=" + position + " mode=" + mAdapter.getMode() +
					" actionState=" + (actionState == ItemTouchHelper.ACTION_STATE_SWIPE ? "Swipe(1)" : "Drag(2)"));
		if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
			if (!alreadySelected) {
				//Be sure, if MODE_MULTI is active, to add this item to the selection list (call listener!)
				//Also be sure user consumes the long click event if not done in onLongClick.
				//Drag by LongPress or Drag by handleView
				if (mLongClickSkipped || mAdapter.getMode() == SelectableAdapter.MODE_MULTI) {
					//Next check, allows to initiate the ActionMode and to add selection if configured
					if ((shouldAddSelectionInActionMode() || mAdapter.getMode() != SelectableAdapter.MODE_MULTI) &&
							mAdapter.mItemLongClickListener != null && mAdapter.isSelectable(position)) {
						mAdapter.mItemLongClickListener.onItemLongClick(position);
						alreadySelected = true; //Keep selection on release!
					}
				}
				//If still not selected, be sure current item appears selected for the Drag transition
				if (!alreadySelected) {
					mAdapter.toggleSelection(position);
				}
			}
			//Now toggle the activation, Activate view and make selection visible only if necessary
			if (!itemView.isActivated()) {
				toggleActivation();
			}
		} else if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE &&
				shouldActivateViewWhileSwiping() && !alreadySelected) {
			mAdapter.toggleSelection(position);
			toggleActivation();
		}
	}

	/**
	 * Here we handle the event of when the ItemTouchHelper has completed the move or swipe.
	 * <p>In this implementation, View activation is automatically handled.</p>
	 * In case of Drag, the state will be cleared depends by current selection mode!
	 *
	 * @param position the position of the item released
	 */
	@Override
	@CallSuper
	public void onItemReleased(int position) {
		if (FlexibleAdapter.DEBUG)
			Log.v(TAG, "onItemReleased position=" + position + " mode=" + mAdapter.getMode() +
					" actionState=" + (mActionState == ItemTouchHelper.ACTION_STATE_SWIPE ? "Swipe(1)" : "Drag(2)"));
		//Be sure to keep selection if MODE_MULTI and shouldAddSelectionInActionMode is active
		if (!alreadySelected) {
			if (shouldAddSelectionInActionMode() &&
					mAdapter.getMode() == SelectableAdapter.MODE_MULTI) {
				mAdapter.mItemLongClickListener.onItemLongClick(position);
				if (mAdapter.isSelected(position)) {
					toggleActivation();
				}
			} else if (shouldActivateViewWhileSwiping() && itemView.isActivated()) {
				mAdapter.toggleSelection(position);
				toggleActivation();
			} else if (mActionState == ItemTouchHelper.ACTION_STATE_DRAG) {
				mAdapter.toggleSelection(position);
				if (itemView.isActivated()) {
					toggleActivation();
				}
			}
		}
		//Reset internal action state ready for next action
		mLongClickSkipped = false;
		mActionState = ItemTouchHelper.ACTION_STATE_IDLE;
	}

	@Override
	public boolean isDraggable() {
		IFlexible item = mAdapter.getItem(getFlexibleAdapterPosition());
		return item != null && item.isDraggable();
	}

	@Override
	public boolean isSwipeable() {
		IFlexible item = mAdapter.getItem(getFlexibleAdapterPosition());
		return item != null && item.isSwipeable();
	}

	@Override
	public View getFrontView() {
		return itemView;
	}

	@Override
	public View getRearLeftView() {
		return null;
	}

	@Override
	public View getRearRightView() {
		return null;
	}

}