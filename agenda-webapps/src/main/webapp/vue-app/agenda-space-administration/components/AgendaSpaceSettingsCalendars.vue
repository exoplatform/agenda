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
    The calendars the space subscribes to, in the space settings drawer: a
    calendar added or removed here waits for the drawer's Save, and is shown
    with what Save will do and an Undo until then. Refresh now stays immediate,
    since it changes no setting.
  -->
  <div class="agenda-space-settings-calendars">
    <p class="text-light-color agenda-space-subscriptions-explanation">
      {{ $t('agenda.calendarSubscription.spaceExplanation', {0: spaceName}) }}
    </p>
    <v-btn
      id="agendaSpaceSettingsAddCalendar"
      :disabled="saving"
      class="btn mb-2"
      @click="add">
      <v-icon size="14" class="me-2">fas fa-plus</v-icon>
      {{ $t('agenda.space.settings.subscriptions.add') }}
    </v-btn>
    <p
      v-if="loaded && !subscriptions.length && !toAdd.length"
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
        :pending="toRemove.includes(subscription.id) && 'remove' || null"
        :error="errors[subscription.id] || null"
        @refreshed="replace"
        @remove="toRemove = toRemove.concat(subscription.id)"
        @restore="restoreRemoved(subscription.id)" />
      <agenda-space-subscription-item
        v-for="addition in toAdd"
        :key="addition.key"
        :subscription="addition"
        :error="errors[addition.key] || null"
        pending="add"
        @restore="toAdd = toAdd.filter(item => item.key !== addition.key)" />
    </v-list>
  </div>
</template>

<script>
import {errorMessageKey} from '../../agenda-common/js/CalendarSubscriptionService.js';

export default {
  props: {
    ownerId: {
      type: Number,
      default: null,
    },
    spaceName: {
      type: String,
      default: '',
    },
    saving: {
      type: Boolean,
      default: false,
    },
  },
  data: () => ({
    subscriptions: [],
    toAdd: [],
    toRemove: [],
    errors: {},
    loaded: false,
    loadError: null,
    lastKey: 0,
  }),
  computed: {
    /**
     * @returns {Boolean} whether a calendar waits to be added or removed
     */
    hasChanges() {
      return !!(this.toAdd.length || this.toRemove.length);
    },
  },
  watch: {
    /**
     * Tells the drawer whether its Save has calendars to add or remove.
     *
     * @param {Boolean} changed whether a calendar waits for the Save
     * @returns {void}
     */
    hasChanges(changed) {
      this.$emit('changed', changed);
    },
  },
  created() {
    this.$root.$on('agenda-calendar-subscription-staged', this.staged);
    this.load();
  },
  beforeDestroy() {
    this.$root.$off('agenda-calendar-subscription-staged', this.staged);
  },
  methods: {
    /**
     * Forgets what was waiting for a Save and lists the space's calendars
     * again; a refusal is shown in place.
     *
     * @returns {Promise} resolved once listed
     */
    load() {
      this.toAdd = [];
      this.toRemove = [];
      this.errors = {};
      this.loaded = false;
      this.loadError = null;
      return this.$calendarSubscriptionService.getSubscriptions(this.ownerId)
        .then(subscriptions => this.subscriptions = subscriptions || [])
        .catch(error => {
          this.subscriptions = [];
          this.loadError = errorMessageKey(error && error.message);
        })
        .finally(() => this.loaded = true);
    },
    /**
     * Opens the drawer adding a calendar link, which hands the calendar back
     * once its link was read.
     *
     * @returns {void}
     */
    add() {
      this.$root.$emit('agenda-calendar-subscription-drawer-open', null, {ownerId: this.ownerId, spaceName: this.spaceName}, {staged: true});
    },
    /**
     * Lists a calendar handed back by the adding drawer, to be added on Save.
     *
     * @param {Object} values {ownerId, url, name} of the calendar
     * @returns {void}
     */
    staged(values) {
      if (values && Number(values.ownerId) === Number(this.ownerId)) {
        this.lastKey++;
        this.toAdd = this.toAdd.concat({...values, key: `toAdd${this.lastKey}`, name: values.name || values.url});
      }
    },
    /**
     * @param {Number} id the subscription no longer to remove
     * @returns {void}
     */
    restoreRemoved(id) {
      this.toRemove = this.toRemove.filter(removedId => removedId !== id);
      this.$delete(this.errors, id);
    },
    /**
     * @param {Object} refreshed the row a refresh returned
     * @returns {void}
     */
    replace(refreshed) {
      this.subscriptions = this.subscriptions.map(row => row.id === refreshed.id && refreshed || row);
    },
    /**
     * Removes, then adds one by one, what waits for the Save. Each change done leaves the
     * waiting list; each refused one stays in it with its message, so a second
     * Save retries only those.
     *
     * @returns {Promise<Number>} the number of changes refused
     */
    save() {
      this.errors = {};
      const removals = this.toRemove.map(id => this.$calendarSubscriptionService.deleteSubscription(id)
        .then(() => {
          this.subscriptions = this.subscriptions.filter(row => row.id !== id);
          this.toRemove = this.toRemove.filter(removedId => removedId !== id);
        })
        .catch(error => this.refused(id, error)));
      return Promise.all(removals)
        .then(() => this.toAdd.slice().reduce((chain, addition) => chain.then(() => this.create(addition)), Promise.resolve()))
        .then(() => {
          this.$root.$emit('agenda-space-subscriptions-changed', this.ownerId);
          this.$root.$emit('agenda-refresh');
          return Object.keys(this.errors).length;
        });
    },
    /**
     * Subscribes the space to a calendar waiting for the Save. The additions are
     * sent one after another: the server reads each link while holding one of
     * the user's two read permits (`MAX_READS_PER_USER`), and refuses a third
     * read running at once.
     *
     * @param {Object} addition the calendar waiting to be added
     * @returns {Promise} resolved once added or refused
     */
    create(addition) {
      return this.$calendarSubscriptionService.createSubscription({
        ownerId: this.ownerId,
        url: addition.url,
        name: addition.url === addition.name ? null : addition.name,
      })
        .then(saved => {
          this.subscriptions = saved ? this.subscriptions.concat(saved) : this.subscriptions;
          this.toAdd = this.toAdd.filter(item => item.key !== addition.key);
        })
        .catch(error => this.refused(addition.key, error));
    },
    /**
     * @param {String|Number} key the row whose change was refused
     * @param {Error} error the refusal
     * @returns {void}
     */
    refused(key, error) {
      this.$set(this.errors, key, errorMessageKey(error && error.message));
    },
  },
};
</script>
