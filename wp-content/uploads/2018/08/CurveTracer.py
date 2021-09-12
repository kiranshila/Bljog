# - HP 4145B Semiconductor Analyzer Automation Script
# - Kiran Shila - Caltech - 2018
# - FET Characterization


# Imports
import visa
import numpy as np
import re
import matplotlib.pyplot as plt
import datetime
import time
import numdifftools as nd
from scipy.interpolate import UnivariateSpline, splrep, splev

# -------------- Hookup ------------------------ #

# SMU2 = VDS
# SMU3 = VGS
# Common Source, grounded to case

# --------------- User Setup ------------------- #

# Device Name
deviceName = "Diramics 2F200"

## Setup Voltage and Steps ##
VDS_start = 0
VDS_stop = 1
VDS_num_steps = 101
VGS_start = -0.2
VGS_step_size = 0.05
VGS_num_steps = 9

## Current Compliances ##
ID_compliance = 100e-3
IG_compliance = 100e-6


# ------------- Script ------------------------ #

# Calculate Step Size for VDS
VDS_step_size = round((VDS_stop - VDS_start)/VDS_num_steps,3)
print("Calculated step size is %f" % VDS_step_size)


# VISA Initialization
address = "GPIB0::17::INSTR"
rm = visa.ResourceManager()
hp = rm.open_resource(address)

# Timestamp for all files
timeString = datetime.datetime.now().strftime("%d_%m_%Y_%H:%M")

# Disable timeout faults
hp.timeout = None
# Check Connection
print(hp.query("ID"))
hp.write("*RST")
hp.write("*CLS")
# Channel Setup
print("Setting up channel")
hp.write("IT1") # Meduium integration time
hp.write("DE,CH2,'VDS','ID',1,1;") # SMU2 to VDS, VDS is VAR1
hp.write("DE,CH3,'VGS','IG',1,2;") # SMU3 to VGS, VGS is VAR2
hp.write("DE,CH1;") # Disable other channels
hp.write("DE,CH4;")
hp.write("DE,VS1;")
hp.write("DE,VS2;")
hp.write("DE,VM1;")
hp.write("DE,VM2;")

# Variable sweep setup
print("Setting up sweep variables")
hp.write("SS VR1,%f,%f,%f,%f;" % (VDS_start,VDS_stop,VDS_step_size,ID_compliance))
hp.write("SS VP%f,%f,%f,%f;" % (VGS_start,VGS_step_size,VGS_num_steps,IG_compliance))

# Setting up display
hp.write("SM;DM1;XN 'VDS',1,%f,%f;YB;YA 'ID',1,%f,%f;" % (VDS_start,VDS_stop,0,ID_compliance))

# Taking measurement
print("Measuring")
hp.write("BC;")
hp.write("DR1;")
hp.write("MD ME1;")
hp.wait_for_srq()
hp.write("DR0;")
hp.write("*WAI")

# WAIT FOR SRQ NO LONGER WORKING
# CHANGE TIME HERE FOR DATA MEASUREMENT
time.sleep(10)

# Formatting Data
print("Getting data:")
data = hp.query("DO 'ID';")
print("Data OK")
print("Parsing data")
filtered = re.findall("\w[\s+-]([eE.\d+-]+)", data)

ID = np.array([float(f) for f in filtered])

if(VGS_num_steps > 1):
    ID = np.split(ID,VGS_num_steps)
VDS = np.arange(VDS_start,VDS_stop+VDS_step_size, VDS_step_size)
for array in ID:
    array *= 1000
print("Data arranged")
print("ID: %d VDS:%d" % (len(ID), len(VDS)))

fig, ax = plt.subplots()

HeaderString = 'VDS (V), ID (mA)\n,'
for counter,item in enumerate(ID):
    ax.plot(VDS,ID[counter].tolist(),label = "VGS=%0.2f" % (VGS_start+VGS_step_size*counter),lw=0.8)
    HeaderString += "VGS=%0.3f," % (VGS_start+VGS_step_size*counter)
HeaderString = HeaderString[:-1] # Remove trailing comma
ax.grid()
ax.set(xlabel='VDS (V)', ylabel='ID (mA)',
       title=deviceName+' IV Curve')
handles, labels = ax.get_legend_handles_labels()
ax.legend(handles, labels)
#plt.show()
CSV_Data = np.column_stack(ID)
VDS_column = np.transpose(np.array(VDS))
CSV_Data = np.concatenate((VDS_column[:,np.newaxis],CSV_Data),axis=1)

# Save image and CSV
plt.savefig(deviceName+"_IV-" + timeString + "_SweptVDS.png",dpi=500)

np.savetxt(deviceName+"_IV-" + timeString + "_SweptVDS.csv",CSV_Data, delimiter=',', header=HeaderString, comments="")

# -- Measuring GM --
hp.write("DE,CH2,'VDS','ID',1,2;") # SMU2 to VDS, VDS is VAR2
hp.write("DE,CH3,'VGS','IG',1,1;") # SMU3 to VGS, VGS is VAR1

# Recalculate step sizes for transposed data
VGS_stop = VGS_num_steps*VGS_step_size+VGS_start-VGS_step_size
tempSteps = VGS_num_steps
VGS_num_steps = VDS_num_steps
VGS_step_size = round((VGS_stop - VGS_start)/VGS_num_steps,3)
print("Calculated step size is %f" % VGS_step_size)
VDS_num_steps = tempSteps
VDS_step_size = (VDS_stop-VDS_start)/VDS_num_steps
VDS_start = VDS_step_size


# Variable sweep setup
print("Setting up sweep variables")
hp.write("SS VR1,%f,%f,%f,%f;" % (VGS_start,VGS_stop,VGS_step_size,IG_compliance))
hp.write("SS VP%f,%f,%f,%f;" % (VDS_start,VDS_step_size,VDS_num_steps,ID_compliance))

# Setting up display
hp.write("SM;DM1;XN 'VGS',1,%f,%f;YB;YA 'ID',1,%f,%f;" % (VGS_start,VGS_stop,0,ID_compliance))

# Taking measurement
print("Measuring")
hp.write("BC;")
hp.write("DR1;")
hp.write("MD ME1;")
hp.wait_for_srq()
hp.write("DR0;")

time.sleep(10)

# Formatting Data
print("Getting data:")
data = hp.query("DO 'ID';")
print("Data OK")
print("Parsing data")
filtered = re.findall("\w[\s+-]([eE.\d+-]+)", data)

ID = np.array([float(f) for f in filtered])

if(VDS_num_steps > 1):
    ID = np.split(ID,VDS_num_steps)
VGS = np.arange(VGS_start,VGS_stop+VGS_step_size, VGS_step_size)
for array in ID:
    array *= 1000
print("Data arranged")
print("ID: %d VGS:%d" % (len(ID), len(VGS)))

# This is to save swept VGS data, we don't need this right now

# fig, ax = plt.subplots()

# HeaderString = 'VGS (V), ID (mA)\n,'
# for counter,item in enumerate(ID):
#     ax.plot(VGS,ID[counter].tolist(),label = "VDS=%0.2f" % (VDS_start+VDS_step_size*counter))
#     HeaderString += "VDS=%0.3f," % (VDS_start+VDS_step_size*counter)
# HeaderString = HeaderString[:-1] # Remove trailing comma
# ax.grid()
# ax.set(xlabel='VGS (V)', ylabel='ID (mA)',
#        title=deviceName+' IV Curve')
# handles, labels = ax.get_legend_handles_labels()
# ax.legend(handles, labels)
# #plt.show()
# CSV_Data = np.column_stack(ID)
# VGS_column = np.transpose(np.array(VGS))
# CSV_Data = np.concatenate((VGS_column[:,np.newaxis],CSV_Data),axis=1)

# plt.savefig(deviceName+"_IV-" + datetime.datetime.now().strftime("%d_%m_%Y_%H:%M") + "_SweptVGS.png",dpi=500)

# np.savetxt(deviceName+"_IV-" + datetime.datetime.now().strftime("%d_%m_%Y_%H:%M") + "_SweptVGS.csv",CSV_Data, delimiter=',', header=HeaderString, comments="")

# Close HPIB Connection with 4145B
hp.close()

# Now for the fun part, solving for Gm
x = VGS
gm = []
for y in ID:
    gm.append(splev(x,splrep(x,y,k=5,s=3),der=1))
    # 5th order spline fit, first derivative

fig, ax = plt.subplots()

HeaderString = 'VGS (V), Gm (mS)\n,'
for counter,item in enumerate(gm):
    ax.plot(ID[counter],gm[counter].tolist(),label = "VDS=%0.2f" % (VDS_start+VDS_step_size*counter),lw=0.8)
    HeaderString += "VDS=%0.3f," % (VDS_start+VDS_step_size*counter)
HeaderString = HeaderString[:-1] # Remove trailing comma
ax.grid()
ax.set(xlabel='ID (mA)', ylabel='Gm (mS)',
       title=deviceName+' Gm Curve')
handles, labels = ax.get_legend_handles_labels()
ax.legend(handles, labels)
#plt.show()
CSV_Data = np.column_stack(gm)
VGS_column = np.transpose(np.array(VGS))
CSV_Data = np.concatenate((VGS_column[:,np.newaxis],CSV_Data),axis=1)

# Save figures and CSV
plt.savefig(deviceName+"_IV-" + timeString + "_GM.png",dpi=500)

np.savetxt(deviceName+"_IV-" + timeString + "_GM.csv",CSV_Data, delimiter=',', header=HeaderString, comments="")
