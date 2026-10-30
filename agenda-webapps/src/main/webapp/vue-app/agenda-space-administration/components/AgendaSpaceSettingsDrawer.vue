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
    The agenda settings of a space: the colour its events take and, for a real
    manager of the space, the calendars it subscribes to. Nothing is saved
    before Save, which saves both; Cancel, or closing the drawer, keeps both as
    they were.
  -->
  <exo-drawer
    id="agendaSpaceSettingsDrawer"
    ref="drawer"
    :loading="saving"
    :confirm-close="hasChanges"
    :confirm-close-labels="confirmCloseLabels"
    class="agendaSpaceSettingsDrawer"
    right>
    <template #title>
      {{ $t('agenda.space.settings.title') }}
    </template>
    <template #content>
      <div class="pa-4">
        <div class="d-flex align-center mb-4">
          <div class="text-subtitle-1 text-start flex-grow-1">{{ $t('agenda.space.settings.eventColor') }}</div>
          <color-picker
            :value="color"
            :swatches="$agendaUtils.EVENT_COLOR_SWATCHES"
            :disabled="saving"
            opaque
            @input="color = $event">
            <template #activator="{on, attrs, color: shown}">
              <!-- The colour is drawn, never printed: its code is the tooltip and the accessible name -->
              <v-btn
                id="agendaSpaceSettingsColor"
                :title="shown"
                :aria-label="$t('agenda.space.settings.eventColor.current', {0: shown || ''})"
                :disabled="saving"
                icon
                small
                v-bind="attrs"
                v-on="on">
                <span :style="swatchStyle(shown, 20)" class="d-flex rounded-circle"></span>
              </v-btn>
            </template>
          </color-picker>
        </div>
        <p
          v-if="colorError"
          class="error--text agenda-space-settings-color-error"
          role="alert">
          {{ $t('agenda.space.settings.colorSaveFailed') }}
        </p>
        <template v-if="canManageSubscriptions">
          <div class="text-subtitle-1 text-start mb-2">{{ $t('agenda.space.settings.subscriptions.title') }}</div>
          <agenda-space-settings-calendars
            ref="calendars"
            :owner-id="ownerId"
            :space-name="spaceName"
            :saving="saving"
            @changed="calendarsChanged = $event" />
        </template>
      </div>
    </template>
    <template #footer>
      <div class="d-flex">
        <v-spacer />
        <v-btn
          id="agendaSpaceSettingsCancel"
          :disabled="saving"
          class="btn me-2"
          @click="close">
          {{ $t('agenda.button.cancel') }}
        </v-btn>
        <v-btn
          id="agendaSpaceSettingsSave"
          :disabled="!hasChanges"
          :loading="saving"
          class="btn btn-primary"
          @click="save">
          {{ $t('agenda.button.save') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>

<script>
import {swatchStyle, sameColor} from '../js/SpaceSettingsColor.js';

export default {
  props: {
    calendar: {
      type: Object,
      default: null,
    },
    canManageSubscriptions: {
      type: Boolean,
      default: false,
    },
    spaceName: {
      type: String,
      default: '',
    },
  },
  data: () => ({
    color: null,
    calendarsChanged: false,
    colorError: false,
    saving: false,
  }),
  computed: {
    /**
     * @returns {Number} the identity id of the space, owner of its calendar
     */
    ownerId() {
      return Number(this.calendar && this.calendar.owner && this.calendar.owner.id) || null;
    },
    /**
     * Whether the colour picked differs from the stored one, compared as the
     * picker emits colours (#RRGGBB, upper case) rather than as stored.
     *
     * @returns {Boolean} true when the colour changed
     */
    colorChanged() {
      return !!this.color && !sameColor(this.color, this.calendar && this.calendar.color);
    },
    /**
     * @returns {Boolean} whether Save has something to save
     */
    hasChanges() {
      return this.colorChanged || this.calendarsChanged;
    },
    /**
     * @returns {Object} the labels of the question asked before changes are dropped
     */
    confirmCloseLabels() {
      return {
        title: this.$t('agenda.settings.drawer.confirmCancelChanges.title'),
        message: this.$t('agenda.settings.drawer.confirmCancelChanges'),
        ok: this.$t('agenda.button.yes'),
        cancel: this.$t('agenda.button.no'),
      };
    },
  },
  methods: {
    swatchStyle,
    /**
     * Opens the drawer on the settings as stored.
     *
     * @returns {void}
     */
    open() {
      this.color = this.calendar && this.calendar.color || null;
      this.colorError = false;
      this.calendarsChanged = false;
      this.$refs.drawer.open();
      // Drawn at the first opening only, the calendars list loads itself as it is created
      if (this.$refs.calendars) {
        this.$refs.calendars.load();
      }
    },
    /**
     * Closes the drawer; with changes, the drawer asks first.
     *
     * @returns {void}
     */
    close() {
      this.$refs.drawer.close();
    },
    /**
     * Saves the colour, then the calendars. What was saved stays saved; what
     * was refused stays in the drawer with its message, which stays open.
     *
     * @returns {Promise} resolved once saved or refused
     */
    save() {
      if (!this.hasChanges || this.saving) {
        return Promise.resolve();
      }
      this.saving = true;
      this.colorError = false;
      return this.saveColor()
        .then(() => (this.calendarsChanged && this.$refs.calendars ? this.$refs.calendars.save() : 0))
        .then(refusedCalendars => {
          if (this.colorError || refusedCalendars) {
            this.$root.$emit('alert-message', this.$t('agenda.space.settings.saveFailed'), 'error');
            return;
          }
          // The drawer reads its confirm-close prop at the next render, once the saved settings are no change any more
          return this.$nextTick().then(() => this.$refs.drawer.close());
        })
        .finally(() => this.saving = false);
    },
    /**
     * Saves the space's calendar with the colour picked, when it changed.
     *
     * @returns {Promise} resolved once saved or refused
     */
    saveColor() {
      if (!this.colorChanged) {
        return Promise.resolve();
      }
      const color = this.color;
      const calendarToSave = JSON.parse(JSON.stringify(this.calendar));
      calendarToSave.color = color;
      return this.$calendarService.saveCalendar(calendarToSave)
        .then(() => this.$emit('color-saved', color))
        .catch(() => this.colorError = true);
    },
  },
};
</script>
