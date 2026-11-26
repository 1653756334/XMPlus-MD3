# Project-specific R8 rules.

# jaudiotagger is a desktop library: its artwork helpers reach for AWT and ImageIO, which Android does not have.
# The app writes covers itself (see FlacCover, SongTags), so those classes are never loaded.
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**
