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
        <div>
          <select class="width-auto my-auto ml-4 pr-2 subtitle-1 ignore-vuetify-classes d-none d-sm-inline">
            <option>My Event</option>
            <option>All Events</option>
          </select>
        </div>
        <v-spacer />
        <agenda-settings-button />
        <agenda-settings-button />
      </v-toolbar>
    </v-flex>
  </v-layout>
</template>
