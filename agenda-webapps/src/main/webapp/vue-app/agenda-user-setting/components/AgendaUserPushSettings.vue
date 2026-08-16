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
        <div v-if="pushEnabled" class="text-subtitle mt-2">
          <template v-if="mirrorCalendarName">
            {{ $t('agenda.settings.pushEventsDestination', {0: mirrorCalendarName}) }}
          </template>
          <template v-else>
            {{ $t('agenda.settings.pushEventsNoDestination') }}
          </template>
          <a
            class="ms-1"
            href="#"
            @click.prevent="configureDestination">
            {{ $t('agenda.settings.pushEventsConfigure') }}
          </a>
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
     * @returns {Object} the connector, or null when none is connected
     */
    connectedConnector() {
      return this.connectors
        && this.connectors.find(connector => connector && connector.isSignedIn && connector.canPush) || null;
    },
  },
  watch: {
    settings: {
      immediate: true,
      handler() {
        // absent means enabled: the platform pushed by default long before
        // this switch existed, and reading it as 'off' would silently stop
        // copying for everyone who never opened this page
        this.pushEnabled = !this.settings || this.settings.automaticPushEvents !== false;
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
    this.$root.$on('agenda-connector-mirror-calendar-done', this.retrieveDestination);
  },
  beforeDestroy() {
    this.$root.$off('agenda-connector-mirror-calendar-done', this.retrieveDestination);
  },
  methods: {
    /**
     * Stores the new value of the setting, and puts the switch back where it
     * was when the save fails, so it never shows a state the server does not
     * hold.
     * @returns {void}
     */
    savePushSetting() {
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
     * Reads the name of the calendar the copies are written to, from the
     * connector's own list rather than from a separately kept copy, so a
     * calendar renamed in the user's own client reads correctly here.
     * @returns {void}
     */
    retrieveDestination() {
      const connector = this.connectedConnector;
      if (!connector || typeof connector.getMirrorCalendarId !== 'function') {
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
      this.$root.$emit('agenda-connector-mirror-calendar-open', this.connectedConnector);
    },
  },
};
</script>
