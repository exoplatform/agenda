<template>
  <v-app v-if="displayed">
    <v-card class="ma-4 border-radius" flat>
      <v-list two-line>
        <agenda-user-general-settings :settings="settings" />
        <agenda-user-connector-settings :settings="settings" />
      </v-list>
    </v-card>
  </v-app>
</template>

<script>
export default {
  data: () => ({
    displayed: true,
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