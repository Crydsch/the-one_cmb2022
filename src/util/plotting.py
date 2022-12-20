import sys
import re
import os
from argparse import ArgumentParser
from math import nan


try:
    import seaborn as sns
    import matplotlib.pyplot as plt
    from matplotlib.ticker import FixedLocator, MaxNLocator
    import pandas as pd
except ImportError:
    print('Failed to load dependencies. Please ensure that seaborn, matplotlib, and pandas can be loaded.', file=sys.stderr)
    exit(-1)

def exportToPdf(fig, filename):
    """
    Exports the current plot to file. In the 8:6 format.
    """

    # Saving plot to slide format 8:6
    fig.set_figwidth(8)
    fig.set_figheight(6)
    
    fig.savefig(filename, bbox_inches='tight', format='pdf')
    print(f"Wrote output to {filename}")

def plotLinestring(df, title, xdata, ydata, output, xmin=0, ymin=0, hue=None):
    """
    Plotting a linestring from given dataframe and saving to the specified output path.
    """

    fig, ax = plt.subplots()

    if hue is None:
        sns.lineplot(data=df, x=xdata, y=ydata, marker="o")
    else:
        sns.lineplot(data=df, x=xdata, y=ydata, marker="o", hue=hue)

    plt.title(title)
    ax.set_xlim(xmin=xmin)
    ax.set_ylim(ymin=ymin)
    plt.grid()
    
    exportToPdf(fig, output)


def plotBarplot(df, title, xdata, ydata, output, xmin=0, ymin=0, hue=None):
    fig, ax = plt.subplots()

    if hue is None:
        sns.barplot(data=df, x=xdata, y=ydata)
    else:
        sns.barplot(data=df, x=xdata, y=ydata, hue=hue)

    plt.title(title)
    ax.set_xlim(xmin=xmin)
    ax.set_ylim(ymin=ymin)
    plt.grid()
    
    exportToPdf(fig, output)

def plotContactTimes(args):
    """
    Gets the path to the contactTimeData and plots it
    """

    contactTimePath = args.input
    contactTimePath = contactTimePath[0]

    df = pd.read_csv(contactTimePath, sep=" ", header=None, names=["time", "contactTime"])
    plotLinestring(df, "ContactTime", "time", "contactTime", args.output)

def parseMessageCopyCount(path):
    """
    Parsing the message copy count report and returns a pandas dataframe.
    """

    data = []
    with open(path) as file:
        for line in file:
            if line.startswith("["):
                timestamp = re.findall("[\d]+", line)
                # print(timestamp)
                if timestamp is None:
                    print("Something went wrong during parsing! Cannot extract timestamp in line: ", line)
                    exit(1)
                timestamp = int(timestamp[0])
            if line.startswith("M"):
                message = re.findall("M([\d]+) ([\d]+)", line)[0]
                # print(message)
                if message is None or len(message) < 2:
                    print("Something went wrong during parsing! Cannot extract count in line: ", line)
                    exit(1)
                name = "M" + message[0]
                count = int(message[1])
                data.append({"Time":timestamp, "MessageName":name,"CopyCount":count})
    
    df = pd.DataFrame(data)
    print(df)
    return df

def plotMessageCopyCount(args):
    """
    Requires the path to the MessageCopyCount report and plots the number of messages copied at every captured time point.
    """

    copyCountPath = args.input[0]
    df = parseMessageCopyCount(copyCountPath)

    plotLinestring(df=df, title="Message Copy Count", xdata="Time", ydata="CopyCount", output=args.output, hue="MessageName")


if __name__ == '__main__':

    parser = ArgumentParser(description='Generate performance charts for throughput values from pcap file')
    parser.add_argument('-i', '--input', action="append", default=[], required=True)
    parser.add_argument('-o', '--output', type=str, required=True)
    parser.add_argument('-x', '--xaxis', type=str, required=False)
    parser.add_argument('-y', '--yaxis', type=str, required=False)
    parser.add_argument('-t', '--title', type=str, required=False)

    result = parser.parse_args()

    if len(result.input) > 1:
        exit(0)
    else:
        inp = result.input[0]
        inp = inp.lower()
        if inp.__contains__("contacttime"):
            plotContactTimes(args=result)
        elif inp.__contains__("messagecopy"):
            plotMessageCopyCount(args=result)