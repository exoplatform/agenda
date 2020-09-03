<template>
  <v-menu
    v-model="selectedOpen"
    :close-on-content-click="false"
    :activator="selectedElement"
    offset-x>
    <v-card
      color="grey lighten-4"
      flat>
      <v-toolbar
        :color="selectedEvent.color"
        dark>
        <v-btn icon>
          <v-icon>mdi-pencil</v-icon>
        </v-btn>
        <v-toolbar-title>{{ selectedEvent.name }}</v-toolbar-title>
        <v-spacer />
        <v-btn icon>
          <v-icon>mdi-heart</v-icon>
        </v-btn>
        <v-btn icon>
          <v-icon>mdi-dots-vertical</v-icon>
        </v-btn>
      </v-toolbar>
      <v-card-text>
        <span>{{ selectedEvent.details }}</span>
      </v-card-text>
      <v-card-actions>
        <v-btn
          text
          color="secondary"
          @click="selectedOpen = false">
          Cancel
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-menu>
</template>

<script>
export default {
  data: () => ({
    selectedOpen:false,
    selectedEvent: {},
    selectedElement: null,
  })
};
</script>
