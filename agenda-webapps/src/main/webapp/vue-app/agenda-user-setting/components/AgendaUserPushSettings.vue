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
          The check sits next to the destination because it answers the
          question the destination raises: the name shown here was read once,
          and says nothing about whether that calendar still accepts what eXo
          sends it. Kept as a button the user presses rather than something
          run on display: it talks to a server outside the platform, and a
          settings page must not hang on it, nor probe an account every time
          someone opens their preferences.
          No flex here on purpose: the align-center helper of this skin also
          centres text, which silently centres the whole caption.
        -->
        <div v-if="mirrorCalendarName" class="text-subtitle mt-2">
          {{ $t('agenda.settings.pushEventsDestination', {0: mirrorCalendarName}) }}
          <v-btn
            :loading="checking"
            :disabled="checking"
            class="ms-1"
            small
            text
            @click="checkDestination">
            {{ $t('agenda.settings.pushEventsCheck') }}
          </v-btn>
        </div>
        <div v-if="checkMessage" class="text-subtitle mt-1">
          <v-icon
            :color="checkColor"
            size="16"
            class="me-1">
            {{ checkIcon }}
          </v-icon>
          {{ checkMessage }}
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
    checking: false,
    checkOutcome: null,
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
     * The four outcomes are told apart because the action each calls for is
     * different: nothing to do, recreate the calendar, reconnect the account,
     * wait and retry. Lumping them into one "check failed" is what left a
     * rejected password looking like a server problem.
     *
     * @returns {String} the message to display, empty until a check has run
     */
    checkMessage() {
      switch (this.checkOutcome) {
      case 'reachable': return this.$t('agenda.settings.pushEventsCheckReachable', {0: this.mirrorCalendarName});
      case 'missing': return this.$t('agenda.settings.pushEventsCheckMissing');
      case 'credentials': return this.$t('agenda.settings.pushEventsCheckCredentials');
      case 'unreachable': return this.$t('agenda.settings.pushEventsCheckUnreachable');
      default: return '';
      }
    },
    /**
     * The icon standing beside the outcome, so the answer is readable before
     * the sentence is: found, or not.
     *
     * @returns {String} the icon class of the last outcome
     */
    checkIcon() {
      return this.checkOutcome === 'reachable' && 'fas fa-check-circle' || 'fas fa-times-circle';
    },
    /**
     * The colour of that icon. Everything that is not a working destination
     * is an error and not a warning: the copies are not arriving in any of
     * those three cases, which is the same outcome for the user.
     *
     * @returns {String} the colour class of the last outcome
     */
    checkColor() {
      return this.checkOutcome === 'reachable' && 'success' || 'error';
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
      // A destination that is being re-read makes the previous verdict stale:
      // it was about a calendar that may no longer be the one named below.
      this.checkOutcome = null;
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
    /**
     * Asks the connected account, live, whether the calendar named above can
     * still receive the copies, and says what it answered.
     *
     * Listing the account's calendars is the whole check: it needs the
     * credentials to be accepted, and it enumerates the collections that
     * exist, which is exactly the two ways the destination stops working —
     * the account was refused, or the calendar was deleted from another
     * client. So no dedicated endpoint is needed to answer the question, and
     * none is added: a "is it still there" API would have to be kept in step
     * with what the copies actually do, whereas this asks the very thing they
     * ask.
     *
     * The name shown above is refreshed on the way, since the list was just
     * read — a calendar renamed in the user's own client would otherwise keep
     * reading here under its old name.
     *
     * @returns {Promise} resolves once the outcome has been established
     */
    checkDestination() {
      const connector = this.mirrorCapableConnector;
      if (!connector) {
        return;
      }
      this.checking = true;
      this.checkOutcome = null;
      return Promise.resolve(connector.getMirrorCalendarId())
        .then(mirrorId => connector.listCalendars()
          .then(calendars => {
            const mirror = (calendars || [])
              .find(calendar => this.$remoteEventConnector.isSameCalendarHref(calendar.id, mirrorId));
            this.mirrorCalendarName = mirror && mirror.name || this.mirrorCalendarName;
            this.checkOutcome = mirror && 'reachable' || 'missing';
          }))
        .catch(error => this.checkOutcome = this.failedCheckOutcome(connector, error))
        .finally(() => this.checking = false);
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
