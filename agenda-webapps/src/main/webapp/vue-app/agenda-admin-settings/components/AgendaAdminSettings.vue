<template>
  <v-app class="connectorsAdminSettings">
    <v-main class="white rounded-lg pa-5 card-border-radius">
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
    this.refreshSettings()
      .finally(() => this.$root.$applicationLoaded());
  },
  methods: {
    refreshSettings() {
      return this.$settingsService.getUserSettings()
        .then(settings => this.settings = settings);
    },
  }
};
</script>