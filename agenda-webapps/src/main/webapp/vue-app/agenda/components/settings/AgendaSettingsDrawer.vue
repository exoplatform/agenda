<template>
  <exo-drawer
    ref="agendaSettingsDrawer"
    right
    @opened="drawer = true"
    @closed="drawer = false">
    <template slot="title">
      {{ $t('agenda.agendaPreferences') }}
    </template>
    <template slot="content">
      <v-tabs
        v-model="calendarType"
        fixed-tabs
        centered
        class="pa-4 text-capitalize">
        <v-tabs-slider />
        <v-tab href="#agendas" dark>
          {{ $t('agenda.agendas') }}
        </v-tab>
        <v-tab href="#settings" dark>
          {{ $t('agenda.settings') }}
        </v-tab>
        <v-tab href="#sync" dark>
          {{ $t('agenda.sync') }}
        </v-tab>
        <v-tab-item value="agendas">
          <agenda-calendars-tab />
        </v-tab-item>
        <v-tab-item value="settings">
          <agenda-settings-tab />
        </v-tab-item>
        <v-tab-item value="sync">
          <agenda-sync-tab />
        </v-tab-item>
      </v-tabs>
    </template>
  </exo-drawer>
</template>

<script>
export default {
  data: () => ({
    drawer: false,
  }),
  computed: {
  },
  watch: {
  },
  created() {
    this.$root.$on('agenda-settings-drawer-open', this.open);
  },
  methods: {
    close() {
      this.$refs.agendaSettingsDrawer.close();
    },
    open() {
      this.$refs.agendaSettingsDrawer.open();
    },
  },
};
</script>