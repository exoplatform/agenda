<template>
  <v-app class="connectorsAdminSettings">
    <v-main class="white rounded-lg ma-5 px-7 pb-2">
      <agenda-admin-connector-settings :settings="settings" />
      <agenda-admin-conference-settings :settings="settings" />
    </v-main>
  </v-app>
</template>

<script>
export default {
  data: () => ({
    settings: null,
  }),
  created() {
    this.refreshSettings();
  },
  methods: {
    refreshSettings() {
      this.$settingsService.getUserSettings()
        .then(settings => this.settings = settings);
    },
  }
};
</script>