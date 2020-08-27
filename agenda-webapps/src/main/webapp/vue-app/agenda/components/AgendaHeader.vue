<template>
  <v-layout justify-center>
    <v-flex xs12>
      <v-toolbar
        color="white"
        class="ma-5"
        flat
        dense>
        <create-event-button />
        <sync-event-button />
        <agenda-switch-view />
        <agenda-search-event />
        <agenda-pulldown-event />
        <v-spacer />
        <agenda-filter-button />
        <agenda-settings-button />
      </v-toolbar>
    </v-flex>
  </v-layout>
</template>
