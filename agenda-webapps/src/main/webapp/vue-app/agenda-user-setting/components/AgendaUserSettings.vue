<template>
  <v-app v-if="displayed">
    <v-card class="application-body" flat>
      <v-list two-line>
        <agenda-user-general-settings :settings="settings" />
        <agenda-user-connector-settings
          :settings="settings"
          @connectors-loaded="connectors = $event" />
        <agenda-user-push-settings
          :settings="settings"
          :connectors="connectors" />
      </v-list>
    </v-card>
    <!--
      The step that creates the calendar receiving the copies. Mounted here
      rather than inside the connector row: connecting from this page used to
      have nowhere to offer it, so the account was created and the destination
      never asked for.
    -->
    <agenda-connector-mirror-calendar-drawer />
  </v-app>
</template>

<script>
export default {
  data: () => ({
    displayed: true,
    connectors: [],
    settings: {
      agendaDefaultView: 'week',
      agendaWeekStartOn: 'MO',
      showWorkingTime: false,
      workingTimeStart: '08:00',
      workingTimeEnd: '18:00',
    },
  }),
  created() {
    this.$root.$on('agenda-settings-refresh', this.initSettings);
    this.initSettings();
  },
  mounted() {
    document.addEventListener('hideSettingsApps', (event) => {
      if (event && event.detail && this.id !== event.detail) {
        this.displayed = false;
      }
    });
    document.addEventListener('showSettingsApps', () => this.displayed = true);
    this.$root.$on('agenda-settings-refresh', this.initSettings);
  },
  methods: {
    initSettings(userSettings) {
      if (userSettings) {
        this.settings = userSettings;
        this.$root.$applicationLoaded();
      } else {
        return this.$settingsService.getUserSettings()
          .then(settings => {
            if (settings) {
              this.settings = settings;
            }
            return this.$nextTick();
          })
          .finally(() => this.$root.$applicationLoaded());
      }
    },
  },
};
</script>