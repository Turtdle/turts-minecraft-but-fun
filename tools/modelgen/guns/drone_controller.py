"""Skydio Enterprise Controller-inspired ground station, ~30 x 17 cm body.
9 x 5 units with fold-up antennas; 4.7 x 2.7 unit (~7 inch) screen.
Both player-skin hands grip the sides; display tilts 28 degrees toward the eye.
"""
import drones as D
from armorygen import Model


def model() -> Model:
    return D.controller(D.new_model("drone_controller"))
