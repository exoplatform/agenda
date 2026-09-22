/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see gnu.org/licenses.
 */
<template>
  <!--
    Subscribing to a calendar link, and editing a subscription (EXO-90278). One
    instance for the whole application, mounted beside the other calendar drawers
    in Agenda.vue and opened by a root event: never inside a row, which can go
    away while the drawer is open (EXO-90239).
  -->
  <exo-drawer
    ref="subscriptionDrawer"
    :loading="busy"
    :autofocus="false"
    class="agendaCalendarSubscriptionDrawer"
    right
    @closed="colorMenu = false">
    <template slot="title">
      {{ title }}
    </template>
    <template slot="content">
      <form
        class="mx-4 mt-4"
        @submit.stop.prevent="save">
        <p class="text-light-color">{{ $t('agenda.calendarSubscription.explanation') }}</p>
        <!--
          The link, its row and its red or green message form one block with its
          own bottom margin: the gap to the Name label is the same with or without
          a message, where a top margin on the label did not show.
        -->
        <div class="agenda-calendar-subscription-link-block mb-6">
          <v-label for="agendaCalendarSubscriptionUrl">
            {{ $t('agenda.calendarSubscription.urlLabel') }}
          </v-label>
          <div class="d-flex align-center mt-2">
            <v-text-field
              id="agendaCalendarSubscriptionUrl"
              v-model="url"
              :placeholder="$t('agenda.calendarSubscription.urlPlaceholder')"
              :aria-label="$t('agenda.calendarSubscription.urlLabel')"
              class="pt-0 flex-grow-1 agenda-calendar-subscription-url"
              type="url"
              maxlength="2048"
              outlined
              dense
              hide-details
              @input="urlChanged" />
            <v-btn
              :loading="state === 'checking'"
              :disabled="!hasUrl || busy"
              class="btn ms-2 agenda-calendar-subscription-check"
              @click="check">
              {{ $t('agenda.calendarSubscription.check') }}
            </v-btn>
          </div>
          <p
            v-if="errorCode"
            class="error--text mt-2 mb-0 agenda-calendar-subscription-error"
            role="alert">
            {{ errorMessage }}
          </p>
          <p
            v-else-if="state === 'checked'"
            class="success--text mt-2 mb-0 agenda-calendar-subscription-checked">
            {{ $t('agenda.calendarSubscription.checked') }}
          </p>
        </div>
        <div class="agenda-calendar-subscription-name-block mb-6">
          <v-label for="agendaCalendarSubscriptionName">
            {{ $t('agenda.calendarSubscription.nameLabel') }}
          </v-label>
          <v-text-field
            id="agendaCalendarSubscriptionName"
            v-model="name"
            :placeholder="$t('agenda.calendarSubscription.namePlaceholder')"
            :aria-label="$t('agenda.calendarSubscription.nameLabel')"
            class="mt-2 pt-0 agenda-calendar-subscription-name"
            type="text"
            maxlength="200"
            outlined
            dense
            hide-details
            @input="nameTouched = true" />
        </div>
        <v-label>
          {{ $t('agenda.calendar.color') }}
        </v-label>
        <div class="d-flex align-center mt-2">
          <v-menu
            v-model="colorMenu"
            :close-on-content-click="false"
            bottom
            left>
            <template #activator="{ on, attrs }">
              <div
                class="d-flex align-center"
                v-bind="attrs"
                v-on="on">
                <v-card
                  v-if="color"
                  :color="color"
                  height="24"
                  width="24"
                  flat />
                <v-card
                  v-else
                  class="d-flex align-center justify-center"
                  height="24"
                  width="24"
                  outlined>
                  <v-icon size="14" class="text-light-color">fa-palette</v-icon>
                </v-card>
                <div class="ms-2">{{ color || $t('agenda.calendar.colorAuto') }}</div>
              </div>
            </template>
            <v-card>
              <v-color-picker
                v-model="newColor"
                :swatches="swatches"
                class="ma-2"
                mode="hexa"
                show-swatches
                flat />
              <v-card-actions>
                <v-spacer />
                <v-btn class="btn ms-2" @click="colorMenu = false">
                  {{ $t('agenda.button.cancel') }}
                </v-btn>
                <v-btn class="btn btn-primary ms-2" @click="applyColor">
                  {{ $t('agenda.button.apply') }}
                </v-btn>
              </v-card-actions>
            </v-card>
          </v-menu>
        </div>
      </form>
    </template>
    <template slot="footer">
      <div class="d-flex">
        <v-spacer />
        <v-btn
          class="btn me-2 agenda-calendar-subscription-cancel"
          @click="close">
          {{ $t('agenda.button.cancel') }}
        </v-btn>
        <v-btn
          :disabled="!canSave"
          :loading="state === 'saving'"
          class="btn btn-primary agenda-calendar-subscription-save"
          @click="save">
          {{ isEdit ? $t('agenda.calendarSubscription.save') : $t('agenda.calendarSubscription.subscribe') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>

<script>
import {errorMessageKey} from '../../js/CalendarSubscriptionService.js';

export default {
  data: () => ({
    subscription: null,
    url: '',
    name: '',
    color: null,
    colorMenu: false,
    newColor: null,
    nameTouched: false,
    state: 'idle',
    errorCode: null,
  }),
  computed: {
    /**
     * Whether an existing subscription is being edited.
     *
     * @returns {Boolean} true when editing
     */
    isEdit() {
      return !!(this.subscription && this.subscription.id);
    },
    /**
     * @returns {String} the drawer title
     */
    title() {
      return this.isEdit ? this.$t('agenda.calendarSubscription.editTitle') : this.$t('agenda.calendarSubscription.subscribeTitle');
    },
    /**
     * @returns {Boolean} whether a link was typed
     */
    hasUrl() {
      return !!(this.url && this.url.trim());
    },
    /**
     * @returns {Boolean} whether a request is running
     */
    busy() {
      return this.state === 'checking' || this.state === 'saving';
    },
    /**
     * A new subscription needs a link; an edit may leave it blank to keep the
     * current one — which is the only way to rename a subscription whose link
     * can no longer be read.
     *
     * @returns {Boolean} whether Subscribe or Save may be pressed
     */
    canSave() {
      return !this.busy && (this.isEdit || this.hasUrl);
    },
    /**
     * @returns {String} the sentence of the last refusal
     */
    errorMessage() {
      return this.$t(errorMessageKey(this.errorCode));
    },
    /**
     * @returns {Array} the colour swatches agenda offers everywhere
     */
    swatches() {
      return this.$agendaUtils && this.$agendaUtils.EVENT_COLOR_SWATCHES || [];
    },
  },
  watch: {
    /**
     * Seeds the picker with the current colour each time it opens.
     *
     * @param {Boolean} opened whether the colour menu just opened
     * @returns {void}
     */
    colorMenu(opened) {
      if (opened) {
        this.newColor = this.color || this.swatches.length && this.swatches[0][0] || '#08a554';
      }
    },
  },
  created() {
    this.$root.$on('agenda-calendar-subscription-drawer-open', this.open);
  },
  beforeDestroy() {
    this.$root.$off('agenda-calendar-subscription-drawer-open', this.open);
  },
  methods: {
    /**
     * Opens the drawer, empty to subscribe or filled to edit a subscription.
     *
     * @param {Object} subscription the subscription to edit, or nothing
     * @returns {void}
     */
    open(subscription) {
      this.subscription = subscription || null;
      this.url = subscription && subscription.url || '';
      this.name = subscription && subscription.name || '';
      this.color = subscription && subscription.color || null;
      this.nameTouched = !!subscription;
      this.state = 'idle';
      this.errorCode = null;
      this.colorMenu = false;
      this.$refs.subscriptionDrawer.open();
    },
    /**
     * Closes the drawer.
     *
     * @returns {void}
     */
    close() {
      this.$refs.subscriptionDrawer.close();
    },
    /**
     * A changed link is no longer the one checked, nor the one refused.
     *
     * @returns {void}
     */
    urlChanged() {
      if (this.state === 'checked') {
        this.state = 'idle';
      }
      this.errorCode = null;
    },
    /**
     * Applies the colour picked.
     *
     * @returns {void}
     */
    applyColor() {
      this.color = this.newColor;
      this.colorMenu = false;
    },
    /**
     * Asks the server to read the link, and names the calendar after it unless
     * the user already typed a name. An answer for a link changed meanwhile is
     * ignored.
     *
     * @returns {Promise} resolved once the answer is shown
     */
    check() {
      if (!this.hasUrl || this.busy) {
        return Promise.resolve();
      }
      const url = this.url.trim();
      this.state = 'checking';
      this.errorCode = null;
      return this.$calendarSubscriptionService.checkUrl(url)
        .then(result => {
          if (this.url.trim() !== url) {
            this.state = 'idle';
            return;
          }
          this.state = 'checked';
          if (!this.nameTouched && result && result.name) {
            this.name = result.name;
          }
        })
        .catch(error => {
          this.state = 'idle';
          this.errorCode = error && error.message || null;
        });
    },
    /**
     * Subscribes, or saves the edit: the server reads a new link before it
     * stores it, and answers a refusal code the drawer shows in place.
     *
     * @returns {Promise} resolved once saved or refused
     */
    save() {
      if (!this.canSave) {
        return Promise.resolve();
      }
      const values = {
        url: this.hasUrl ? this.url.trim() : null,
        name: this.name && this.name.trim() || null,
        color: this.color || null,
      };
      const editing = this.isEdit;
      let request;
      if (editing) {
        if (values.url === this.subscription.url) {
          values.url = null;
        }
        request = this.$calendarSubscriptionService.updateSubscription(this.subscription.id, values);
      } else {
        request = this.$calendarSubscriptionService.createSubscription(values);
      }
      this.state = 'saving';
      this.errorCode = null;
      return request
        .then(saved => {
          this.state = 'idle';
          this.$root.$emit('agenda-refresh-subscribed-calendars');
          this.$root.$emit('agenda-refresh');
          if (!editing) {
            this.$root.$emit('alert-message',
              this.$t('agenda.calendarSubscription.subscribed', {0: saved && saved.name || values.name || ''}),
              'success');
          }
          this.close();
        })
        .catch(error => {
          this.state = 'idle';
          this.errorCode = error && error.message || null;
        });
    },
  },
};
</script>
