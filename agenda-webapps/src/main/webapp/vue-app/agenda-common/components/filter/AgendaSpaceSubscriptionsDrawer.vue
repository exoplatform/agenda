<!--
 Copyright (C) 2026 eXo Platform SAS.

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License
 as published by the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program. If not, see <gnu.org/licenses>.
-->
<template>
  <!--
    The calendars a space subscribes to (EXO-90373), for its managers: add one by
    link, and for each who added it and when, when it was last refreshed, a
    warning when its last refresh failed, and Refresh now / Remove. Opened by a
    root event from the space calendar's menu in the personal agenda and from the
    space settings; mounted once on each page, never in a row. The server refuses
    anyone but a real manager of the space; the openers are only drawn for them.
  -->
  <exo-drawer
    ref="spaceSubscriptionsDrawer"
    :loading="loading"
    class="agendaSpaceSubscriptionsDrawer"
    right>
    <template slot="title">
      {{ $t('agenda.calendarSubscription.spaceDrawerTitle') }}
    </template>
    <template slot="content">
      <div class="mx-4 mt-4">
        <p class="text-light-color agenda-space-subscriptions-explanation">
          {{ $t('agenda.calendarSubscription.spaceExplanation', {0: spaceName}) }}
        </p>
        <v-btn
          class="btn btn-primary mb-4 agenda-space-subscriptions-add"
          @click="add">
          <v-icon size="14" class="me-2">fas fa-plus</v-icon>
          {{ $t('agenda.calendarSubscription.add') }}
        </v-btn>
        <p
          v-if="loaded && !subscriptions.length"
          class="text-light-color agenda-space-subscriptions-empty">
          {{ $t('agenda.calendarSubscription.spaceEmpty') }}
        </p>
        <p
          v-if="loadError"
          class="error--text agenda-space-subscriptions-load-error"
          role="alert">
          {{ $t(loadError) }}
        </p>
        <v-list class="pa-0" dense>
          <agenda-space-subscription-item
            v-for="subscription in subscriptions"
            :key="subscription.id"
            :subscription="subscription"
            @refreshed="replace"
            @remove="confirmRemove(subscription)" />
        </v-list>
      </div>
      <exo-confirm-dialog
        ref="removeConfirmDialog"
        :title="$t('agenda.calendarSubscription.removeConfirmTitle')"
        :message="removeMessage"
        :ok-label="$t('agenda.calendarSubscription.remove')"
        :cancel-label="$t('agenda.button.cancel')"
        @ok="remove" />
    </template>
  </exo-drawer>
</template>

<script>
import {errorMessageKey} from '../../js/CalendarSubscriptionService.js';

export default {
  data: () => ({
    ownerId: null,
    spaceName: '',
    subscriptions: [],
    loading: false,
    loaded: false,
    loadError: null,
    toRemove: null,
  }),
  computed: {
    /**
     * @returns {String} the confirmation sentence of a removal
     */
    removeMessage() {
      return this.toRemove && this.$t('agenda.calendarSubscription.removeConfirmMessage', {0: this.toRemove.name, 1: this.spaceName}) || '';
    },
  },
  created() {
    this.$root.$on('agenda-space-subscriptions-drawer-open', this.open);
    this.$root.$on('agenda-space-subscriptions-changed', this.changed);
  },
  beforeDestroy() {
    this.$root.$off('agenda-space-subscriptions-drawer-open', this.open);
    this.$root.$off('agenda-space-subscriptions-changed', this.changed);
  },
  methods: {
    /**
     * Opens the drawer on a space and lists its subscriptions.
     *
     * @param {Object} space {ownerId, spaceName}: the space's identity id and
     *          display name
     * @returns {Promise} resolved once listed
     */
    open(space) {
      this.ownerId = space && space.ownerId || null;
      this.spaceName = space && space.spaceName || '';
      this.subscriptions = [];
      this.loaded = false;
      this.loadError = null;
      this.toRemove = null;
      this.$refs.spaceSubscriptionsDrawer.open();
      return this.retrieve();
    },
    /**
     * Reads the space's subscriptions again, once a calendar was added to it.
     *
     * @param {Number} ownerId the space whose subscriptions changed
     * @returns {Promise} resolved once listed
     */
    changed(ownerId) {
      return ownerId && Number(ownerId) === Number(this.ownerId) ? this.retrieve() : Promise.resolve();
    },
    /**
     * Lists the space's subscriptions; a refusal is shown in place.
     *
     * @returns {Promise} resolved once listed
     */
    retrieve() {
      if (!this.ownerId) {
        return Promise.resolve();
      }
      this.loading = true;
      return this.$calendarSubscriptionService.getSubscriptions(this.ownerId)
        .then(subscriptions => {
          this.subscriptions = subscriptions || [];
          this.loadError = null;
        })
        .catch(error => {
          this.subscriptions = [];
          this.loadError = errorMessageKey(error && error.message);
        })
        .finally(() => {
          this.loading = false;
          this.loaded = true;
        });
    },
    /**
     * Opens the drawer adding a calendar link, for this space.
     *
     * @returns {void}
     */
    add() {
      this.$root.$emit('agenda-calendar-subscription-drawer-open', null, {ownerId: this.ownerId, spaceName: this.spaceName});
    },
    /**
     * Shows a subscription as its refresh answered.
     *
     * @param {Object} refreshed the row the refresh returned
     * @returns {void}
     */
    replace(refreshed) {
      this.subscriptions = this.subscriptions.map(row => row.id === refreshed.id && refreshed || row);
    },
    /**
     * Asks before removing.
     *
     * @param {Object} subscription the row
     * @returns {void}
     */
    confirmRemove(subscription) {
      this.toRemove = subscription;
      this.$refs.removeConfirmDialog.open();
    },
    /**
     * Removes the confirmed subscription from the space: its row goes and the
     * agenda reads the events again.
     *
     * @returns {Promise} resolved once done
     */
    remove() {
      const subscription = this.toRemove;
      if (!subscription) {
        return Promise.resolve();
      }
      return this.$calendarSubscriptionService.deleteSubscription(subscription.id)
        .then(() => {
          this.subscriptions = this.subscriptions.filter(row => row.id !== subscription.id);
          this.toRemove = null;
          this.$root.$emit('agenda-space-subscriptions-removed', this.ownerId);
          this.$root.$emit('alert-message', this.$t('agenda.calendarSubscription.removed', {0: subscription.name, 1: this.spaceName}), 'success');
          this.$root.$emit('agenda-refresh');
        })
        .catch(error => this.$root.$emit('alert-message', this.$t(errorMessageKey(error && error.message)), 'error'));
    },
  },
};
</script>
