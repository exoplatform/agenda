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
      <!-- text-color, the class the E-mail rows on this same page use:
           text-header renders grey and lighter, so the calendar rows read
           as a different kind of setting than the ones above them. -->
      <v-list-item-title class="text-color">
        {{ $t('agenda.settings.pushEvents') }}
      </v-list-item-title>
      <!-- No vertical margin: the E-mail rows on this same page sit their
           summary straight under their header, and a row that breathes more
           than its neighbours reads as belonging to another list. -->
      <v-list-item-subtitle>
        <span>
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
        <!--
          The destination, and — only when something is wrong with it — why.
          There used to be a "Check it" button beside this line, from when the
          name was read once and said nothing about whether that calendar still
          accepted what eXo sends. The destination is now read from the server
          on every render, so the button asked a question the render had
          already answered; what was worth keeping is the verdict, not the act
          of asking for it.
          No flex here on purpose: the align-center helper of this skin also
          centres text, which silently centres the whole caption.
        -->
        <div v-if="mirrorCalendarName" class="text-subtitle">
          {{ $t('agenda.settings.pushEventsDestination', {0: mirrorCalendarName}) }}
        </div>
        <div v-if="problemMessage" class="text-subtitle mt-1">
          <v-icon
            color="warning"
            size="16"
            class="me-1">
            fa-exclamation-triangle
          </v-icon>
          {{ problemMessage }}
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
    destinationProblem: null,
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
    /**
     * What the last check found, in words the user can act on.
     *
     * The three cases are told apart because the action each calls for is
     * different: recreate the calendar, reconnect the account, wait and retry.
     * Lumping them into one "it did not work" is what left a rejected
     * password looking like a server problem.
     *
     * Nothing is said when the destination reads correctly. A line confirming
     * that the thing named on the line above exists is noise: the name is
     * only there because it was just read from the server.
     *
     * @returns {String} the message to display, empty while all is well
     */
    problemMessage() {
      switch (this.destinationProblem) {
      case 'missing': return this.$t('agenda.settings.pushEventsCheckMissing');
      case 'credentials': return this.$t('agenda.settings.pushEventsCheckCredentials');
      case 'unreachable': return this.$t('agenda.settings.pushEventsCheckUnreachable');
      default: return '';
      }
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
     * Reads the calendar the copies are written to, and says what is wrong
     * when it cannot be read.
     *
     * Asked of the connector on every render rather than kept: the calendar
     * lives on a server outside the platform, where it can be renamed,
     * deleted, or made unreachable by a password that stopped working. A name
     * remembered from an earlier read would keep claiming copies are arriving
     * somewhere they are not.
     *
     * This is what replaced the "Check it" button. The button existed because
     * the name was read once and said nothing about now; reading it now
     * answers the same question without asking the user to press anything.
     *
     * @returns {void}
     */
    retrieveDestination() {
      this.destinationProblem = null;
      const connector = this.mirrorCapableConnector;
      if (!connector) {
        this.mirrorCalendarName = null;
        return;
      }
      Promise.resolve(this.readMirror(connector))
        .then(mirror => {
          this.mirrorCalendarName = mirror && mirror.name || null;
          // Only a destination the user chose can be missing. Never having
          // chosen one is not a problem to report — it is the state the
          // switch itself already shows.
          this.destinationProblem = !mirror && this.pushEnabled && 'missing' || null;
          this.refreshSwitch();
        })
        .catch(error => {
          this.mirrorCalendarName = null;
          this.destinationProblem = this.failedCheckOutcome(connector, error);
        });
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
    /**
     * Reads the calendar the copies go to, and the name it carries now.
     *
     * A connector that can answer directly is asked directly. The older
     * contract — an id, then a scan of the calendar listing for it — is kept
     * for connectors that only have that, but it cannot be the first choice:
     * a connector is free to leave the destination out of that listing, and
     * the CalDAV one does exactly that (the collection holds nothing but
     * copies of events the agenda already shows). The scan then found
     * nothing, this screen concluded there was no destination, and the switch
     * went back off in front of the user who had just chosen one.
     *
     * @param {Object} connector the connector holding the destination
     * @returns {Promise<Object>} {id, name}, or null when there is none
     */
    readMirror(connector) {
      if (typeof connector.getMirrorCalendar === 'function') {
        return connector.getMirrorCalendar();
      }
      return Promise.resolve(connector.getMirrorCalendarId())
        .then(mirrorId => mirrorId && connector.listCalendars()
          .then(calendars => (calendars || [])
            .find(calendar => this.$remoteEventConnector.isSameCalendarHref(calendar.id, mirrorId)) || null) || null);
    },
    /**
     * Tells apart the reasons the check could not reach a verdict.
     *
     * A refused password is singled out because it is the failure a user can
     * fix and the one they are least able to recognise: a connector may react
     * to a rejected credential by probing the server and then report
     * something that reads as a broken address rather than a bad password.
     *
     * Which code means that is the connector's to say, not this page's: it
     * declares `credentialsErrorCode`, and a connector that declares none
     * simply never produces this outcome. Naming one connector's code here
     * would put an add-on's vocabulary in a component that must serve every
     * connector.
     *
     * @param {Object} connector the connector whose check failed
     * @param {Object} error the failure the connector rejected with
     * @returns {String} the outcome to display
     */
    failedCheckOutcome(connector, error) {
      console.error('cannot check the calendar receiving the copies', error);
      const credentialsCode = connector && connector.credentialsErrorCode;
      return credentialsCode && error && error.code === credentialsCode && 'credentials' || 'unreachable';
    },
  },
};
</script>
