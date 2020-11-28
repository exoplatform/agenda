<template>
  <v-app v-if="displayed">
    <v-card class="ma-4 border-radius" flat>
      <v-list two-line>
        <agenda-user-general-settings />
        <agenda-user-connector-settings />
      </v-list>
    </v-card>
  </v-app>
</template>

<script>
export default {
  data: () => ({
    displayed: true,
  }),
  mounted() {
    document.addEventListener('hideSettingsApps', (event) => {
      if (event && event.detail && this.id !== event.detail) {
        this.displayed = false;
      }
    });
    document.addEventListener('showSettingsApps', () => this.displayed = true);
  },
};
</script>