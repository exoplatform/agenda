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
 * along with this program. If not, see <gnu.org/licenses>.
 */
<template>
  <v-list-item v-if="displayed">
    <v-list-item-content>
      <v-list-item-title class="text-header">
        {{ $t('agenda.settings.pushEvents') }}
      </v-list-item-title>
      <v-list-item-subtitle class="my-3">
        <span class="text-subtitle">
          {{ $t('agenda.settings.pushEventsSubTitle') }}
        </span>
        <!--
          Naming the destination is the point of the line: the copies land in
          a calendar of the connected account, and without saying which one a
          user has no way of knowing where their meetings went.
        -->
        <!--
          Shown only once a destination exists, and stating it rather than
          offering to change it: the step creates the calendar and has no
          other choice to make, so a control here would promise one that does
          not exist. Turning the switch off and on again is what reopens the
          step, which is also how a calendar deleted from another client gets
          made again.
        -->
        <div v-if="mirrorCalendarName" class="text-subtitle mt-2">
          {{ $t('agenda.settings.pushEventsDestination', {0: mirrorCalendarName}) }}
        </div>
      </v-list-item-subtitle>
    </v-list-item-content>
    <v-list-item-action>
      <v-switch
        v-model="pushEnabled"
        :loading="saving"
        :disabled="saving"
        class="mt-0 me-2"
        hide-details
        @change="savePushSetting" />
    </v-list-item-action>
  </v-list-item>
</template>

<script>
export default {
  props: {
    settings: {
      type: Object,
      default: () => null,
    },
    connectors: {
      type: Array,
      default: () => [],
    },
  },
  data: () => ({
    pushEnabled: false,
    saving: false,
    mirrorCalendarName: null,
  }),
  computed: {
    /**
     * Whether the setting is worth showing at all: only an account that can
     * receive copies makes the question meaningful, so a deployment with no
     * pushing connector never shows a switch that would do nothing.
     *
     * @returns {Boolean} true when a connected connector can push
     */
    displayed() {
      return this.connectedConnector !== null;
    },
    /**
     * The connected connector able to push, if any.
     *
     * Selected on `connected`, never on `isSignedIn`: several connectors
     * declare isSignedIn as a static true — it says the connector needs no
     * sign-in of its own, not that an account is attached — so picking on it
     * returns a connector nobody connected, whose destination cannot be read
     * and whose calendar step refuses to open.
     *
     * @returns {Object} the connector, or null when none is connected
     */
    connectedConnector() {
      return (this.connectors || [])
        .find(connector => connector && connector.connected && connector.canPush) || null;
    },
    /**
     * The connected connector that can hold a destination calendar, which is
     * not every pushing connector: the ones predating the mirror write to the
     * account's first calendar and have nothing to configure.
     *
     * @returns {Object} the connector, or null when none can
     */
    mirrorCapableConnector() {
      return (this.connectors || [])
        .find(connector => connector
          && connector.connected
          && connector.canCreateCalendar
          && typeof connector.getMirrorCalendarId === 'function') || null;
    },
  },
  watch: {
    settings: {
      immediate: true,
      handler() {
        this.refreshSwitch();
      },
    },
    connectors: {
      immediate: true,
      handler() {
        this.retrieveDestination();
      },
    },
  },
  created() {
    this.$root.$on('agenda-connector-mirror-calendar-done', this.destinationChosen);
    this.$root.$on('agenda-connector-mirror-calendar-cancelled', this.destinationDeclined);
  },
  beforeDestroy() {
    this.$root.$off('agenda-connector-mirror-calendar-done', this.destinationChosen);
    this.$root.$off('agenda-connector-mirror-calendar-cancelled', this.destinationDeclined);
  },
  methods: {
    /**
     * Stores the new value of the setting, and puts the switch back where it
     * was when the save fails, so it never shows a state the server does not
     * hold.
     * @returns {void}
     */
    savePushSetting() {
      // Turning it on without a destination is the state to avoid: the copies
      // would go to whichever calendar the account happens to list first. So
      // the step is asked for here, and the switch only stays on once it has
      // been answered.
      if (this.pushEnabled && this.mirrorCapableConnector && !this.mirrorCalendarName) {
        this.configureDestination();
        return;
      }
      this.storePushSetting();
    },
    /**
     * Writes the setting, putting the switch back where it was if the save
     * fails, so it never shows a state the server does not hold.
     * @returns {void}
     */
    storePushSetting() {
      const settings = Object.assign({}, this.settings, {automaticPushEvents: this.pushEnabled});
      this.saving = true;
      this.$settingsService.saveUserSettings(settings)
        .then(() => this.$root.$emit('agenda-settings-refresh'))
        .catch(() => {
          this.pushEnabled = !this.pushEnabled;
          this.$root.$emit('alert-message', this.$t('agenda.settings.pushEventsError'), 'error');
        })
        .finally(() => this.saving = false);
    },
    /**
     * A destination was chosen: the switch may now stay on, and the setting
     * is written to match it.
     * @returns {void}
     */
    destinationChosen() {
      this.retrieveDestination();
      this.pushEnabled = true;
      this.storePushSetting();
    },
    /**
     * The user backed out of choosing a destination, so the switch goes back
     * off: copying was never turned on, and claiming otherwise would send the
     * copies somewhere nobody picked.
     * @returns {void}
     */
    destinationDeclined() {
      if (!this.mirrorCalendarName) {
        this.pushEnabled = false;
      }
    },
    /**
     * Puts the switch where the stored state says it belongs. For a connector
     * holding a destination, on means both that copying is enabled and that a
     * destination exists — the two halves are one thing to the user, and
     * showing on without a destination is what sends copies astray. A
     * connector with no destination to hold reads the setting alone.
     * @returns {void}
     */
    refreshSwitch() {
      const enabled = !this.settings || this.settings.automaticPushEvents !== false;
      this.pushEnabled = this.mirrorCapableConnector
        ? enabled && !!this.mirrorCalendarName
        : enabled;
    },
    /**
     * Reads the name of the calendar the copies are written to, from the
     * connector's own list rather than from a separately kept copy, so a
     * calendar renamed in the user's own client reads correctly here.
     * @returns {void}
     */
    retrieveDestination() {
      const connector = this.mirrorCapableConnector;
      if (!connector) {
        this.mirrorCalendarName = null;
        return;
      }
      Promise.resolve(connector.getMirrorCalendarId())
        .then(mirrorId => {
          if (!mirrorId) {
            this.mirrorCalendarName = null;
            return;
          }
          return connector.listCalendars()
            .then(calendars => {
              const mirror = (calendars || [])
                .find(calendar => this.$remoteEventConnector.isSameCalendarHref(calendar.id, mirrorId));
              this.mirrorCalendarName = mirror && mirror.name || null;
              this.refreshSwitch();
            });
        })
        .catch(() => this.mirrorCalendarName = null);
    },
    /**
     * Opens the step that creates the calendar receiving the copies. It is
     * offered once at connection time and never again, so without this a user
     * who dismissed it then has no way back to it.
     * @returns {void}
     */
    configureDestination() {
      this.$root.$emit('agenda-connector-mirror-calendar-open', this.mirrorCapableConnector);
    },
  },
};
</script>
