RoboButlerApp
=============

Android App to control RoboButler 3000.

To build, you'll need IOIOLib.

After checking this repository out:

    cd RoboButlerApp
    android update project --path .

Then edit project.properties and change the path to IOIOLib to suit your installation.


To regenerate the application icons from the SVG:

    convert -background none resource-src/svg/cocktail-robot.svg -resize 36x36 res/drawable-ldpi/cocktail_robot.png
    convert -background none resource-src/svg/cocktail-robot.svg -resize 48x48 res/drawable-mdpi/cocktail_robot.png
    convert -background none resource-src/svg/cocktail-robot.svg -resize 72x72 res/drawable-hdpi/cocktail_robot.png

You shouldn't need to do this unless you change the SVG, since the pngs are commited to git as well.